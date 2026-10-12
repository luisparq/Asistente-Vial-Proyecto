<?php
namespace App\Http\Controllers;
use App\Services\PlatformOverview;
use Illuminate\Http\Request;
use Illuminate\Validation\Rule;
class PlatformOverviewController extends Controller {
    public function __invoke(Request $request,PlatformOverview $overview) {
        $data=$request->validate(['days'=>['nullable',Rule::in(['7','30'])]]);
        return view('platform.overview',$overview->data((int)($data['days']??7)));
    }
}
