<?php
namespace App\Http\Requests;
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
use App\Support\AuditDisplay;
class AuditFilterRequest extends FormRequest {
    public function authorize(): bool {return $this->user('web')?->role==='platform_admin';}
    protected function prepareForValidation(): void {$this->merge(['from'=>$this->input('from')?:CarbonImmutable::now('America/Lima')->subDays(29)->format('Y-m-d'),'to'=>$this->input('to')?:CarbonImmutable::now('America/Lima')->format('Y-m-d')]);}
    public function rules(): array {
        return ['from'=>'required|date_format:Y-m-d','to'=>'required|date_format:Y-m-d|after_or_equal:from',
            'actor_id'=>'nullable|integer|exists:users,id','company_id'=>'nullable|integer|exists:companies,id',
            'action'=>['nullable',Rule::in(array_keys(AuditDisplay::ACTIONS))],'module'=>['nullable',Rule::in(array_keys(AuditDisplay::MODULES))],
            'resource_id'=>'nullable|integer|min:1','q'=>'nullable|string|max:100','sort'=>['nullable',Rule::in(['occurred_at','id'])],
            'direction'=>['nullable',Rule::in(['asc','desc'])],'page'=>'nullable|integer|min:1|max:100000'];
    }
    public function withValidator($validator): void {$validator->after(function($v){
        foreach(array_diff(array_keys($this->all()),array_keys($this->rules())) as $key)$v->errors()->add($key,'Filtro no admitido.');
        if(!$v->errors()->isNotEmpty() && CarbonImmutable::parse($this->input('from'))->diffInDays(CarbonImmutable::parse($this->input('to')))>365)$v->errors()->add('to','El rango máximo es de 366 días.');
    });}
}
