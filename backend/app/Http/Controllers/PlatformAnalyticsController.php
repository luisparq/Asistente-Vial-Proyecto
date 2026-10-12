<?php
namespace App\Http\Controllers;
use App\Http\Requests\PlatformAnalyticsRequest;
use App\Services\PlatformAnalytics;
class PlatformAnalyticsController extends Controller {
    public function index(PlatformAnalyticsRequest $r,PlatformAnalytics $analytics) { return view('platform.analytics',$analytics->data($r->validated())); }
    public static function csvCell(string $value): string {
        return preg_match('/^[\s\x00-\x1f]*[=+\-@]|^[\t\r\n]/u',$value) ? "'".$value : $value;
    }
    public function export(PlatformAnalyticsRequest $r,PlatformAnalytics $analytics) {
        $filters=$r->validated();
        return response()->streamDownload(function() use($filters,$analytics) {
            $out=fopen('php://output','w');fwrite($out,"\xEF\xBB\xBF");
            fputcsv($out,['Desde (Lima)','Hasta (Lima)','Empresa','Viajes iniciados','Eventos','SIMULATED (prueba)','SENSOR (no validado)','Frenadas','Aceleraciones','Giros'],';', '"', '');
            foreach($analytics->comparison($filters)->cursor() as $row) {
                $cells=[$filters['from'],$filters['to'],$row->name,$row->trips_count,$row->events_count,$row->simulated_count,$row->sensor_count,$row->braking_count,$row->acceleration_count,$row->turn_count];
                fputcsv($out,array_map(fn($value)=>self::csvCell((string)$value),$cells),';','"','');
            } fclose($out);
        },'actividad-empresas-'.$filters['from'].'-'.$filters['to'].'.csv',['Content-Type'=>'text/csv; charset=UTF-8']);
    }
}
