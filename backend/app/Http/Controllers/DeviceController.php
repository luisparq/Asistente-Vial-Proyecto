<?php
namespace App\Http\Controllers;
use App\Http\Requests\DeviceReportRequest;
use App\Models\{Company, Device};
use App\Support\UtcTimestamp;
use Carbon\CarbonImmutable;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
class DeviceController extends Controller {
    public function report(DeviceReportRequest $request, string $client_device_id) {
        Validator::make(['id'=>$client_device_id], ['id'=>['required','uuid:4']])->validate();
        $user=$request->user();
        abort_unless($user->role === 'driver' && $user->permits('trips:write') &&
            $user->driver()->where('company_id',$user->company_id)->where('status','ACTIVE')->exists(),403);
        $data=$request->validated(); $id=strtolower($client_device_id);
        return DB::transaction(function () use ($user,$id,$data) {
            Company::whereKey($user->company_id)->lockForUpdate()->firstOrFail();
            $device=Device::where('company_id',$user->company_id)->where('client_device_id',$id)->first();
            if ($device) abort_unless($device->user_id === $user->id,403);
            $observed=CarbonImmutable::parse($data['observed_at']);
            if ($device?->observed_at && $observed->lte($device->observed_at)) {
                if ($observed->equalTo($device->observed_at)) {
                    foreach (['name','ble_state','imu_state','gps_state','technical_error'] as $field)
                        abort_if(($data[$field] ?? null) !== $device->$field,409,'Reporte diferente con la misma fecha.');
                    abort_if(UtcTimestamp::format($device->last_contact_at) !== ($data['last_contact_at'] ?? null),409);
                }
                // A delayed offline report never refreshes the communication clock.
                return response()->json(['data'=>['client_device_id'=>$id,'observed_at'=>UtcTimestamp::format($device->observed_at)]]);
            }
            $device ??= (new Device)->forceFill(['company_id'=>$user->company_id,'user_id'=>$user->id,'client_device_id'=>$id]);
            $device->fill($data)->forceFill(['observed_at'=>$observed,'last_contact_at'=>isset($data['last_contact_at']) ? CarbonImmutable::parse($data['last_contact_at']) : null,'received_at'=>now()])->save();
            return response()->json(['data'=>['client_device_id'=>$id,'observed_at'=>UtcTimestamp::format($device->observed_at)]]);
        });
    }
    public function fleet(Request $request) {
        return view('devices.index',['global'=>false,'devices'=>Device::where('company_id',$request->user()->company_id)->with(['user.driver.currentAssignment.vehicle'])->orderByDesc('received_at')->paginate(30)]);
    }
    public function platform() {
        return view('devices.index',['global'=>true,'devices'=>Device::with(['company','user.driver.currentAssignment.vehicle'])->orderBy('company_id')->orderByDesc('received_at')->paginate(30)]);
    }
}
