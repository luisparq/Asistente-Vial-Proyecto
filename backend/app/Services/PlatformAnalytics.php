<?php
namespace App\Services;
use App\Models\{Company,DrivingEvent,Trip};
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\DB;
use App\Support\DashboardDisplay;
class PlatformAnalytics {
    public const MAP_LIMIT=1000;
    private function range(array $f): array {
        return [CarbonImmutable::parse($f['from'],'America/Lima')->startOfDay()->utc()->format('Y-m-d H:i:s.v'),
            CarbonImmutable::parse($f['to'],'America/Lima')->addDay()->startOfDay()->utc()->format('Y-m-d H:i:s.v')];
    }
    public function trips(array $f) {
        [$start,$end]=$this->range($f);
        return Trip::where('started_at','>=',$start)->where('started_at','<',$end)->when($f['company_id']??null,fn($q,$id)=>$q->where('company_id',$id));
    }
    public function events(array $f) {
        [$start,$end]=$this->range($f);
        return DrivingEvent::where('occurred_at','>=',$start)->where('occurred_at','<',$end)
            ->when($f['company_id']??null,fn($q,$id)=>$q->where('company_id',$id))
            ->when($f['type']??null,fn($q,$type)=>$q->where('type',$type))->when($f['origin']??null,fn($q,$origin)=>$q->where('origin',$origin));
    }
    public function comparison(array $f) {
        [$start,$end]=$this->range($f);
        $event=function($q) use($f,$start,$end) {
            $q->where('occurred_at','>=',$start)->where('occurred_at','<',$end);
            foreach(['type','origin'] as $field) if(!empty($f[$field])) $q->where($field,$f[$field]);
        };
        $trip=fn($q)=>$q->where('started_at','>=',$start)->where('started_at','<',$end);
        $counts=['trips'=>$trip,'events'=>$event];
        foreach(['SIMULATED','SENSOR'] as $origin) $counts['events as '.strtolower($origin).'_count']=function($q) use($event,$origin) {$event($q);$q->where('origin',$origin);};
        foreach(['HARSH_BRAKING'=>'braking','HARSH_ACCELERATION'=>'acceleration','SHARP_TURN'=>'turn'] as $type=>$key)
            $counts['events as '.$key.'_count']=function($q) use($event,$type) {$event($q);$q->where('type',$type);};
        return Company::select('id','name')->when($f['company_id']??null,fn($q,$id)=>$q->whereKey($id),
            fn($q)=>$q->where(fn($q)=>$q->whereHas('trips',$trip)->orWhereHas('events',$event)))
            ->withCount($counts)->orderBy($f['sort']??'events_count',$f['direction']??'desc')->orderBy('id');
    }
    private function buckets(array $f,string $format): array {
        return $this->events($f)->selectRaw("strftime('$format', occurred_at, '-5 hours') as bucket, origin, count(*) as total")
            ->groupBy('bucket','origin')->get()->groupBy('bucket')->map(fn($rows)=>[
                'simulated'=>(int)$rows->where('origin','SIMULATED')->sum('total'),'sensor'=>(int)$rows->where('origin','SENSOR')->sum('total')])->all();
    }
    public function data(array $f): array {
        $events=$this->events($f);$trips=$this->trips($f);
        $origin=(clone $events)->selectRaw('origin,count(*) as total')->groupBy('origin')->pluck('total','origin');
        $active=DB::query()->fromSub((clone $trips)->select('company_id')->union((clone $events)->select('company_id')),'active')->distinct()->count('company_id');
        $summary=['companies'=>$active,'trips'=>(clone $trips)->count(),'events'=>(int)$origin->sum(),
            'simulated'=>(int)($origin['SIMULATED']??0),'sensor'=>(int)($origin['SENSOR']??0),
            'drivers'=>(clone $trips)->whereHas('driver',fn($q)=>$q->whereColumn('drivers.company_id','trips.company_id'))->distinct()->count('driver_id'),
            'vehicles'=>(clone $trips)->whereHas('vehicle',fn($q)=>$q->whereColumn('vehicles.company_id','trips.company_id'))->distinct()->count('vehicle_id')];
        $byDay=$this->buckets($f,'%Y-%m-%d');$byMonth=$this->buckets($f,'%Y-%m');$byHour=$this->buckets($f,'%H');$byWeek=$this->buckets($f,'%w');
        $daily=[];$monthly=[];$hourly=[];$weekly=[];
        $from=CarbonImmutable::parse($f['from']);$end=CarbonImmutable::parse($f['to']);
        for($day=$from;$day->lte($end);$day=$day->addDay()) $daily[]=['day'=>$day->format('Y-m-d'),'label'=>$day->format('d/m')]+($byDay[$day->format('Y-m-d')]??['simulated'=>0,'sensor'=>0]);
        for($month=$from->startOfMonth();$month->lte($end);$month=$month->addMonth()) $monthly[]=['label'=>$month->format('m/Y')]+($byMonth[$month->format('Y-m')]??['simulated'=>0,'sensor'=>0]);
        for($hour=0;$hour<24;$hour++) $hourly[]=['label'=>sprintf('%02d:00',$hour)]+($byHour[sprintf('%02d',$hour)]??['simulated'=>0,'sensor'=>0]);
        foreach([1=>'Lun',2=>'Mar',3=>'Mié',4=>'Jue',5=>'Vie',6=>'Sáb',0=>'Dom'] as $key=>$label) $weekly[]=['label'=>$label]+($byWeek[$key]??['simulated'=>0,'sensor'=>0]);
        $types=(clone $events)->selectRaw('type,origin,count(*) as total')->groupBy('type','origin')->get()->groupBy('type')->map(fn($rows,$key)=>[
            'label'=>DashboardDisplay::type($key),'simulated'=>(int)$rows->where('origin','SIMULATED')->sum('total'),'sensor'=>(int)$rows->where('origin','SENSOR')->sum('total')])->values()->all();
        $geo=(clone $events)->whereBetween('latitude',[-90,90])->whereBetween('longitude',[-180,180]);$geoCount=(clone $geo)->count();
        $features=$geo->with('company:id,name')->orderByDesc('occurred_at')->orderByDesc('id')->limit(self::MAP_LIMIT)
            ->get(['id','company_id','type','origin','occurred_at','latitude','longitude','location_origin'])->map(fn($e,$i)=>[
                'type'=>'Feature','id'=>'point-'.$i,'geometry'=>['type'=>'Point','coordinates'=>[$e->longitude,$e->latitude]],
                'properties'=>['uuid'=>'point-'.$i,'label'=>DashboardDisplay::type($e->type),'origin'=>$e->origin,'origin_label'=>DashboardDisplay::origin($e->origin),
                    'date'=>DashboardDisplay::date($e->occurred_at),'company'=>$e->company->name,'location_origin'=>$e->location_origin]])->values()->all();
        return compact('summary','daily','monthly','hourly','weekly','types','geoCount')+
            ['comparison'=>$this->comparison($f)->paginate(20)->withQueryString(),'geojson'=>['type'=>'FeatureCollection','features'=>$features],
                'companies'=>Company::orderBy('name')->get(['id','name']),'mapStyle'=>config('maps.style_url'),'filters'=>$f];
    }
}
