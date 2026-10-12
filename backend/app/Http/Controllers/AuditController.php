<?php
namespace App\Http\Controllers;
use App\Http\Requests\AuditFilterRequest;
use App\Models\{AdministrativeAudit as Audit,Company,User};
use App\Support\{AuditDisplay,DashboardDisplay};
use Carbon\CarbonImmutable;
class AuditController extends Controller {
    private function query(array $f) {
        $q=Audit::where('occurred_at','>=',CarbonImmutable::parse($f['from'],'America/Lima')->utc()->format('Y-m-d H:i:s'))
            ->where('occurred_at','<',CarbonImmutable::parse($f['to'],'America/Lima')->addDay()->utc()->format('Y-m-d H:i:s'));
        foreach(['actor_id','company_id','action'] as $key)if(!empty($f[$key]))$q->where($key,$f[$key]);
        if(!empty($f['module']))$q->where('entity',$f['module']);if(!empty($f['resource_id']))$q->where('entity_id',$f['resource_id']);
        // Search catalogued labels / resource IDs, never sensitive JSON or email fields.
        if(!empty($f['q'])){$ids=array_keys(array_filter(AuditDisplay::ACTIONS,fn($label)=>mb_stripos($label,$f['q'])!==false));
            $q->where(function($q)use($ids,$f){$q->whereIn('action',$ids);if(ctype_digit($f['q']))$q->orWhere('entity_id',(int)$f['q'])->orWhere('id',(int)$f['q']);});}
        return $q;
    }
    public function index(AuditFilterRequest $r) {
        $filters=$r->validated();$query=$this->query($filters);
        return view('platform.audits',['filters'=>$filters,'total'=>(clone $query)->count(),
            'actions'=>(clone $query)->selectRaw('action,count(*) as total')->groupBy('action')->orderByDesc('total')->get(),
            'modules'=>(clone $query)->selectRaw('entity,count(*) as total')->groupBy('entity')->orderByDesc('total')->get(),
            'audits'=>$query->with(['actor','company'])->orderBy($filters['sort']??'occurred_at',$filters['direction']??'desc')->orderByDesc('id')->paginate(25)->withQueryString(),
            'actors'=>User::whereHas('auditRecords')->orderBy('name')->get(['id','name']), 'companies'=>Company::orderBy('name')->get(['id','name'])]);
    }
    public function show(int $id) {return view('platform.audit-detail',['audit'=>Audit::with(['actor','company'])->findOrFail($id)]);}
    public function export(AuditFilterRequest $r) {
        $f=$r->validated();return response()->streamDownload(function()use($f){
            $out=fopen('php://output','w');fwrite($out,"\xEF\xBB\xBF");
            fputcsv($out,['Fecha y hora (Lima)','Responsable','Rol','Empresa','Acción','Módulo','Recurso ID','Resultado','Campos modificados'],';','"','');
            foreach($this->query($f)->with(['actor','company'])->orderBy($f['sort']??'occurred_at',$f['direction']??'desc')->orderByDesc('id')->lazy(200) as $a) {
                $cells=[DashboardDisplay::date($a->occurred_at),$a->actor?->name??'Responsable no disponible',AuditDisplay::role($a),$a->company?->name??'Plataforma / empresa no disponible',
                    AuditDisplay::description($a),AuditDisplay::MODULES[$a->entity]??'Módulo no catalogado',AuditDisplay::resource($a),AuditDisplay::result($a),implode(', ',array_map(fn($field)=>AuditDisplay::FIELDS[$field],AuditDisplay::fields($a)))];
                fputcsv($out,array_map(fn($v)=>PlatformAnalyticsController::csvCell((string)$v),$cells),';','"','');
            }fclose($out);
        },'auditoria-'.$f['from'].'-'.$f['to'].'.csv',['Content-Type'=>'text/csv; charset=UTF-8']);
    }
}
