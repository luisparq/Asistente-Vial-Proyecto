<?php
namespace App\Services;
use App\Models\{Company, Device, Driver, DrivingEvent, Trip, User, Vehicle};
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;

/** Read-only global aggregates, exposed exclusively through PlatformAdmin middleware. */
class PlatformOverview {
    public function data(int $days): array {
        $now=CarbonImmutable::now();
        $local=$now->setTimezone('America/Lima');
        $from=$local->startOfDay()->subDays($days-1)->utc()->format('Y-m-d H:i:s.v');
        $until=$local->startOfDay()->addDay()->utc()->format('Y-m-d H:i:s.v');
        $companyStates=Company::selectRaw('status, count(*) as total')->groupBy('status')->pluck('total','status');
        $eventOrigins=DrivingEvent::selectRaw('origin, count(*) as total')->groupBy('origin')->pluck('total','origin');
        // Same temporal definition as Device::communicationState; no hardware online inference.
        $cutoff=$now->subMinutes(3)->format('Y-m-d H:i:s.v');$future=$now->addSeconds(30)->format('Y-m-d H:i:s.v');
        $deviceCounts=Device::selectRaw("count(*) as total, sum(case when observed_at is null or received_at is null then 1 else 0 end) as unknown,
            sum(case when observed_at >= ? and observed_at <= ? and received_at >= ? then 1 else 0 end) as recent",[$cutoff,$future,$cutoff])->first();
        $devices=['total'=>(int)$deviceCounts->total,'recent'=>(int)$deviceCounts->recent,'unknown'=>(int)$deviceCounts->unknown];
        $devices['stale']=$devices['total']-$devices['recent']-$devices['unknown'];
        $inventory=['companies'=>(int)$companyStates->sum(),'active'=>(int)($companyStates['ACTIVE']??0),'inactive'=>(int)($companyStates['INACTIVE']??0),
            'admins'=>User::where('role','fleet_admin')->count(),'drivers'=>Driver::count(),'vehicles'=>Vehicle::count(),
            'trips'=>Trip::count(),'events'=>(int)$eventOrigins->sum(),'simulated'=>(int)($eventOrigins['SIMULATED']??0),'sensor'=>(int)($eventOrigins['SENSOR']??0)];
        // SQLite groups UTC persistence into Lima calendar days; fetch grouped counts, not individual records.
        $tripDays=Trip::where('started_at','>=',$from)->where('started_at','<',$until)
            ->selectRaw("date(started_at, '-5 hours') as day, count(*) as total")->groupBy('day')->pluck('total','day');
        $eventDays=DrivingEvent::where('occurred_at','>=',$from)->where('occurred_at','<',$until)
            ->selectRaw("date(occurred_at, '-5 hours') as day, origin, count(*) as total")->groupBy('day','origin')->get()->keyBy(fn($r)=>$r->day.'#'.$r->origin);
        $series=[];
        for($i=0;$i<$days;$i++) {
            $date=$local->startOfDay()->subDays($days-1-$i);$key=$date->format('Y-m-d');
            $series[]=['day'=>$key,'label'=>$date->format('d/m'),'trips'=>(int)($tripDays[$key]??0),
                'simulated'=>(int)($eventDays[$key.'#SIMULATED']->total??0),'sensor'=>(int)($eventDays[$key.'#SENSOR']->total??0)];
        }
        $topCompanies=$this->companyCounts()->withCount(['trips'=>fn($q)=>$q->where('started_at','>=',$from)->where('started_at','<',$until),
            'events as simulated_count'=>fn($q)=>$q->where('origin','SIMULATED')->where('occurred_at','>=',$from)->where('occurred_at','<',$until),
            'events as sensor_count'=>fn($q)=>$q->where('origin','SENSOR')->where('occurred_at','>=',$from)->where('occurred_at','<',$until)])
            ->orderByDesc('trips_count')->orderBy('name')->limit(5)->get()->filter(fn($c)=>$c->trips_count>0);
        // Reception-based activity is separate from event time, so delayed offline delivery is visible.
        $receipts=DB::table('trips')->select('company_id','first_received_at as activity_at')->where('first_received_at','>=',$from)->where('first_received_at','<',$until)
            ->unionAll(DB::table('driving_events')->select('company_id','first_received_at as activity_at')->where('first_received_at','>=',$from)->where('first_received_at','<',$until));
        $latest=DB::query()->fromSub($receipts,'receipts')->selectRaw('company_id, max(activity_at) as last_activity')->groupBy('company_id');
        $activeCompanies=$this->companyCounts()->joinSub($latest,'activity',fn($join)=>$join->on('companies.id','=','activity.company_id'))
            ->addSelect('activity.last_activity')->orderByDesc('activity.last_activity')->orderBy('companies.id')->limit(5)->get();
        return compact('now','days','from','until','inventory','devices','series','topCompanies','activeCompanies')+
            ['latestDevices'=>Device::with('company')->whereNotNull('received_at')->orderByDesc('received_at')->orderByDesc('id')->limit(5)->get()];
    }
    private function companyCounts() {
        return Company::select('companies.*')->addSelect([
            'drivers_count'=>Driver::selectRaw('count(*)')->whereColumn('drivers.company_id','companies.id'),
            'vehicles_count'=>Vehicle::selectRaw('count(*)')->whereColumn('vehicles.company_id','companies.id')]);
    }
}
