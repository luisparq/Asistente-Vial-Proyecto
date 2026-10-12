<?php
namespace App\Http\Requests;
use App\Support\UtcTimestamp;
use Carbon\CarbonImmutable;
use Illuminate\Validation\Rule;
class DeviceReportRequest extends ContractRequest {
    public function rules(): array {
        return ['name'=>['required','string','max:100'], 'observed_at'=>['required',new UtcTimestamp],
            'last_contact_at'=>['nullable',new UtcTimestamp],
            'ble_state'=>['required',Rule::in(['NOT_SELECTED','SELECTED','CONNECTING','CONNECTED','DISCONNECTED','FAILED','PERMISSION_REQUIRED','BLUETOOTH_OFF','RECONNECTING'])],
            'imu_state'=>['required',Rule::in(['DISCONNECTED','WAITING_FOR_DATA','RECEIVING','FAILED'])],
            'gps_state'=>['required',Rule::in(['DISCONNECTED','UNAVAILABLE','WAITING','NO_RECEPTION','NO_FIX','VALID','STALE','INVALID_FRAME'])],
            'technical_error'=>['nullable','string','max:255']];
    }
    public function withValidator(\Illuminate\Validation\Validator $validator): void {
        parent::withValidator($validator);
        $validator->after(function ($v) {
            if ($v->errors()->isNotEmpty()) return;
            $observed = CarbonImmutable::parse($this->input('observed_at'));
            if ($observed->gt(now()->addSeconds(30))) $v->errors()->add('observed_at','El reporte está en el futuro.');
            if ($this->input('last_contact_at') && CarbonImmutable::parse($this->input('last_contact_at'))->gt($observed))
                $v->errors()->add('last_contact_at','El contacto no puede ser posterior al reporte.');
        });
    }
}
