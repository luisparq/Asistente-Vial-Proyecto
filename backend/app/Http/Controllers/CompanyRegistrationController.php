<?php
namespace App\Http\Controllers;
use App\Models\{Company, User};
use App\Services\{AccessInvitations, AdministrativeAudit};
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Illuminate\Validation\ValidationException;

class CompanyRegistrationController extends Controller {
    public function create(Request $r) {
        $r->validate(['submission_token'=>'nullable|uuid','step'=>'nullable|integer|between:1,4']);
        $token=$r->query('submission_token');
        if (! $token) {
            $token=(string) Str::uuid();
            $drafts=$r->session()->get('company_registration',[]);
            $drafts[$token]=['next_step'=>1];
            $r->session()->put('company_registration',array_slice($drafts,-20,null,true));
            return redirect()->route('platform.companies.create',['submission_token'=>$token,'step'=>1]);
        }
        $draft=$this->draft($r,$token);
        if (isset($draft['company_id'])) return redirect()->route('platform.companies.show',$draft['company_id']);
        $step=max(1,min(4,(int)$r->query('step',1),$draft['next_step']));
        return view('platform.company-registration',compact('token','draft','step'));
    }
    public function advance(Request $r) {
        $input=$r->validate(['submission_token'=>'required|uuid','step'=>'required|integer|between:1,3']);
        $token=$input['submission_token'];$draft=$this->draft($r,$token);$step=(int)$input['step'];
        if (isset($draft['company_id'])) return redirect()->route('platform.companies.show',$draft['company_id']);
        abort_if($step>$draft['next_step'],422);
        if ($step===2) $r->merge(['admin_email'=>strtolower(trim((string)$r->input('admin_email')))]);
        $rules=$this->rules();
        $fields=match($step) {1=>['name','trade_name'],2=>['admin_name','admin_email'],3=>['status']};
        $data=$r->validate(array_intersect_key($rules,array_flip($fields)));
        $draft=array_replace($draft,$data,['next_step'=>max($draft['next_step'],$step+1)]);
        $r->session()->put('company_registration.'.$token,$draft);
        return redirect()->route('platform.companies.create',['submission_token'=>$token,'step'=>$step+1]);
    }
    public function store(Request $r, AccessInvitations $invitations) {
        $token=$r->validate(['submission_token'=>'required|uuid'])['submission_token'];
        $draft=$this->draft($r,$token);
        if (isset($draft['company_id'])) return redirect()->route('platform.companies.show',$draft['company_id'])->with('success','Este registro ya se completó. No se creó un duplicado.');
        if ($draft['next_step']!==4) throw ValidationException::withMessages(['registration'=>'Completa los cuatro pasos antes de crear la empresa.']);
        // Validate again at confirmation; an email may have been registered in the meantime.
        $data=validator($draft,$this->rules())->validate();
        [$company,$code]=DB::transaction(function() use($r,$data,$invitations) {
            $company=Company::create(['name'=>$data['name'],'trade_name'=>$data['trade_name']??null]);
            $company->forceFill(['status'=>$data['status']])->save();
            AdministrativeAudit::record($r->user(),$company->id,'company.created',$company,['name','trade_name','status']);
            $user=(new User)->forceFill(['company_id'=>$company->id,'role'=>'fleet_admin','access_status'=>'PENDING',
                'name'=>$data['admin_name'],'email'=>$data['admin_email'],'password'=>bin2hex(random_bytes(32))]);
            $user->save();
            AdministrativeAudit::record($r->user(),$company->id,'fleet_admin.created',$user,['name','email']);
            return [$company,$invitations->issue($r->user(),$user)];
        });
        $r->session()->put('company_registration.'.$token,['company_id'=>$company->id]);
        return redirect()->route('platform.companies.show',$company->id)->with('activation_code',$code)->with('success','Empresa y administrador registrados. Entrega el código de activación de forma privada.');
    }
    private function draft(Request $r,string $token): array {
        $draft=$r->session()->get('company_registration.'.$token);
        if (!is_array($draft)) throw ValidationException::withMessages(['registration'=>'El registro venció. Inicia nuevamente desde Empresas.']);
        return $draft;
    }
    private function rules(): array {
        return ['name'=>'required|string|max:255','trade_name'=>'nullable|string|max:255',
            'admin_name'=>'required|string|max:255','admin_email'=>'required|email|max:255|unique:users,email',
            'status'=>['required',Rule::in(['ACTIVE','INACTIVE'])]];
    }
    public function edit(int $id) { return view('platform.company-edit',['company'=>Company::findOrFail($id)]); }
    public function update(Request $r,int $id) {
        $data=$r->validate(['name'=>'required|string|max:255','trade_name'=>'nullable|string|max:255']);
        $company=Company::findOrFail($id);
        DB::transaction(function() use($r,$company,$data) {
            $company->fill($data)->save();
            AdministrativeAudit::record($r->user(),$company->id,'company.updated',$company,array_keys($data));
        });
        return redirect()->route('platform.companies.show',$id)->with('success','Datos de empresa actualizados.');
    }
}
