<?php
namespace App\Http\Requests;
use App\Domain\{DataOrigin,DrivingEventType};
use Carbon\CarbonImmutable;
use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rule;
class PlatformAnalyticsRequest extends FormRequest {
    public function authorize(): bool { return $this->user('web')?->role==='platform_admin'; }
    protected function prepareForValidation(): void {
        $today=CarbonImmutable::now('America/Lima')->format('Y-m-d');
        $this->merge(['to'=>$this->input('to')?:$today,'from'=>$this->input('from')?:CarbonImmutable::now('America/Lima')->subDays(29)->format('Y-m-d')]);
    }
    public function rules(): array {
        return ['from'=>['required','date_format:Y-m-d'],'to'=>['required','date_format:Y-m-d','after_or_equal:from','before_or_equal:'.CarbonImmutable::now('America/Lima')->format('Y-m-d')],
            'company_id'=>['nullable','integer','exists:companies,id'],'type'=>['nullable',Rule::enum(DrivingEventType::class)],
            'origin'=>['nullable',Rule::enum(DataOrigin::class)],'sort'=>['nullable',Rule::in(['name','trips_count','events_count'])],
            'direction'=>['nullable',Rule::in(['asc','desc'])],'page'=>['nullable','integer','min:1','max:100000']];
    }
    public function withValidator($validator): void {
        $validator->after(function($v) {
            foreach(array_diff(array_keys($this->all()),array_keys($this->rules())) as $field) $v->errors()->add($field,'Filtro no admitido.');
            if($v->errors()->isNotEmpty()) return;
            if(CarbonImmutable::parse($this->input('from'))->diffInDays(CarbonImmutable::parse($this->input('to')))>365)
                $v->errors()->add('to','El rango máximo es de 366 días.');
        });
    }
}
