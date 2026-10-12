<form method="post" action="{{ route('platform.companies.accounts.access',[$company->id,$user->id]) }}" class="actions">@csrf
@if($user->access_status==='PENDING')<button class="button quiet" name="action" value="invite">Generar código de activación</button>
@elseif($user->access_status==='ACTIVE')<button class="button quiet" name="action" value="disable">Deshabilitar acceso</button>
@elseif($user->access_status==='DISABLED')<button class="button quiet" name="action" value="enable">Permitir nueva activación</button>@endif
</form>
