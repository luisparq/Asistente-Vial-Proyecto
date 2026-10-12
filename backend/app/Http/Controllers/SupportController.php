<?php
namespace App\Http\Controllers;
use App\Models\{Company,Device,SupportTicket as Ticket,User};
use App\Services\AdministrativeAudit;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
class SupportController extends Controller {
    private function global(Request $r): bool {return $r->user()->role==='platform_admin';}
    private function base(Request $r): string {return $this->global($r)?'platform.support':'dashboard.support';}
    private function query(Request $r) {return Ticket::query()->when(!$this->global($r),fn($q)=>$q->where('company_id',$r->user()->company_id));}
    private function ticket(Request $r,int $id): Ticket {return $this->query($r)->findOrFail($id);}
    private function strict(Request $r,array $rules): array {
        $v=validator($r->except('_token','_method'),$rules);
        $v->after(function($v)use($r,$rules){foreach(array_diff(array_keys($r->except('_token','_method')),array_keys($rules)) as $key)$v->errors()->add($key,'Campo no admitido.');});
        return $v->validate();
    }
    private function deviceRule(int $company): array {return ['nullable','integer',Rule::exists('devices','id')->where('company_id',$company)];}
    private function staffRule(): array {return ['nullable','integer',Rule::exists('users','id')->where('role','platform_admin')->where('access_status','ACTIVE')];}
    public function index(Request $r) {
        $global=$this->global($r);$base=$this->base($r);
        $rules=['q'=>'nullable|string|max:100','status'=>['nullable',Rule::in(array_keys(Ticket::STATUSES))],
            'priority'=>['nullable',Rule::in(array_keys(Ticket::PRIORITIES))],'category'=>['nullable',Rule::in(array_keys(Ticket::CATEGORIES))],
            'company_id'=>['nullable','integer',Rule::exists('companies','id')->when(!$global,fn($rule)=>$rule->where('id',$r->user()->company_id))],
            'device_id'=>['nullable','integer',Rule::exists('devices','id')->when(!$global,fn($rule)=>$rule->where('company_id',$r->user()->company_id))],
            'sort'=>['nullable',Rule::in(['updated_at','created_at','priority','status'])],'direction'=>['nullable',Rule::in(['asc','desc'])],'page'=>'nullable|integer|min:1|max:100000'];
        $filters=$this->strict($r,$rules);$query=$this->query($r);
        foreach(['status','priority','category','company_id','device_id'] as $field) if(!empty($filters[$field]))$query->where($field,$filters[$field]);
        if(!empty($filters['q']))$query->where(fn($q)=>$q->where('code','like','%'.$filters['q'].'%')->orWhere('subject','like','%'.$filters['q'].'%'));
        $direction=$filters['direction']??'desc';$sort=$filters['sort']??'updated_at';
        if($sort==='priority')$query->orderByRaw("case priority when 'HIGH' then 3 when 'MEDIUM' then 2 else 1 end ".$direction);else$query->orderBy($sort,$direction);
        $metrics=$this->query($r)->selectRaw('status,count(*) as total')->groupBy('status')->pluck('total','status');
        $pending=$this->query($r)->where('status','OPEN')->with('company')->orderByRaw("case priority when 'HIGH' then 1 when 'MEDIUM' then 2 else 3 end")->oldest()->limit(5)->get();
        $openCompanies=$this->query($r)->whereIn('status',['OPEN','IN_PROGRESS'])->selectRaw('company_id,count(*) as total')->groupBy('company_id')->with('company')->orderByDesc('total')->limit(5)->get();
        return view('support.index',compact('global','base','metrics','pending','openCompanies','filters')+
            ['tickets'=>$query->with(['company','responsible'])->orderByDesc('id')->paginate(20)->withQueryString(),
                'companies'=>$global?Company::orderBy('name')->get(['id','name']):collect()]);
    }
    public function create(Request $r) {
        $global=$this->global($r);$base=$this->base($r);
        return view('support.create',compact('global','base')+['companies'=>$global?Company::orderBy('name')->get(['id','name']):collect(),
            'devices'=>Device::when(!$global,fn($q)=>$q->where('company_id',$r->user()->company_id))->with('company')->orderBy('company_id')->get(['id','company_id','name','client_device_id'])]);
    }
    public function store(Request $r) {
        $global=$this->global($r);$company=$global?(int)$r->input('company_id'):(int)$r->user()->company_id;
        $rules=['subject'=>'required|string|max:180','description'=>'required|string|max:5000','category'=>['required',Rule::in(array_keys(Ticket::CATEGORIES))],
            'priority'=>['required',Rule::in(array_keys(Ticket::PRIORITIES))],'device_id'=>$this->deviceRule($company)];
        if($global)$rules['company_id']='required|integer|exists:companies,id';
        $data=$this->strict($r,$rules);
        $ticket=DB::transaction(function()use($r,$data,$company){
            $ticket=(new Ticket)->forceFill($data+['company_id'=>$company,'created_by'=>$r->user()->id,'code'=>'AV-'.Str::ulid(),'status'=>'OPEN']);
            $ticket->saveOrFail();
            $ticket->entries()->create(['actor_id'=>$r->user()->id,'kind'=>'CREATED','changes'=>['status'=>['to'=>'OPEN']],'created_at'=>now()]);
            AdministrativeAudit::record($r->user(),$company,'support.created',$ticket,['subject','category','priority','device_id']);
            return $ticket;
        });
        return redirect()->route($this->base($r).'.show',$ticket->id)->with('success','Incidencia registrada.');
    }
    public function show(Request $r,int $id) {
        $ticket=$this->ticket($r,$id)->load(['company','device','creator','responsible']);$global=$this->global($r);$base=$this->base($r);
        return view('support.show',compact('ticket','global','base')+['entries'=>$ticket->entries()->with('actor')->orderByDesc('id')->paginate(20),
            'devices'=>Device::where('company_id',$ticket->company_id)->get(['id','name','client_device_id']),
            'staff'=>$global?User::where('role','platform_admin')->where('access_status','ACTIVE')->get(['id','name']):collect()]);
    }
    public function update(Request $r,int $id) {
        abort_unless($this->global($r),403);$ticket=$this->ticket($r,$id);
        $data=$this->strict($r,['status'=>['required',Rule::in(array_keys(Ticket::STATUSES))],'priority'=>['required',Rule::in(array_keys(Ticket::PRIORITIES))],
            'device_id'=>$this->deviceRule($ticket->company_id),'responsible_id'=>$this->staffRule(),'version'=>'required|integer|min:1']);
        DB::transaction(function()use($r,$ticket,$data){
            $current=Ticket::whereKey($ticket->id)->lockForUpdate()->firstOrFail();abort_if($current->version!=(int)$data['version'],409,'El ticket cambió. Recarga antes de actualizar.');
            $changes=[];foreach(['status','priority','device_id','responsible_id'] as $key)if(array_key_exists($key,$data) && (string)$current->$key!==(string)($data[$key]??null))$changes[$key]=['from'=>$current->$key,'to'=>$data[$key]??null];
            if(!$changes)return;
            $current->fill(array_diff_key($data,['version'=>true]))->forceFill(['version'=>$current->version+1])->save();
            $current->entries()->create(['actor_id'=>$r->user()->id,'kind'=>'UPDATED','changes'=>$changes,'created_at'=>now()]);
            $action='support.updated';
            if(($changes['status']['to']??null)==='CLOSED')$action='support.closed';
            elseif(isset($changes['status']) && in_array($changes['status']['from'],['CLOSED','RESOLVED'],true) && in_array($changes['status']['to'],['OPEN','IN_PROGRESS'],true))$action='support.reopened';
            elseif(count($changes)===1 && isset($changes['priority']))$action='support.priority_changed';
            elseif(count($changes)===1 && isset($changes['responsible_id']))$action='support.assigned';
            AdministrativeAudit::record($r->user(),$current->company_id,$action,$current,array_keys($changes),$changes);
        });return back()->with('success','Ticket actualizado.');
    }
    public function comment(Request $r,int $id) {
        $ticket=$this->ticket($r,$id);$data=$this->strict($r,['comment'=>'required|string|max:3000']);
        DB::transaction(function()use($r,$ticket,$data){
            $current=Ticket::whereKey($ticket->id)->lockForUpdate()->firstOrFail();
            $current->entries()->create($data+['actor_id'=>$r->user()->id,'kind'=>'COMMENT','created_at'=>now()]);
            $current->forceFill(['version'=>$current->version+1])->save();
            AdministrativeAudit::record($r->user(),$ticket->company_id,'support.comment',$ticket,['comment']);
        });return back()->with('success','Comentario registrado.');
    }
    public function device(Request $r,int $id) {
        $global=$this->global($r);$base=$this->base($r);
        $device=Device::when(!$global,fn($q)=>$q->where('company_id',$r->user()->company_id))->with('company')->findOrFail($id);
        return view('support.device',compact('global','base','device')+['tickets'=>$this->query($r)->where('company_id',$device->company_id)->where('device_id',$id)->latest('updated_at')->paginate(20)]);
    }
}
