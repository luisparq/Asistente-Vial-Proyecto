<?php
namespace App\Support;
use App\Models\AdministrativeAudit;
class AuditDisplay {
    public const MODULES=['companies'=>'Empresas','users'=>'Cuentas','drivers'=>'Conductores','vehicles'=>'Vehículos','driver_documents'=>'Documentos','devices'=>'Dispositivos','support_tickets'=>'Soporte'];
    public const ACTIONS=['company.created'=>'Se registró una nueva empresa','company.updated'=>'Se modificaron datos de una empresa','company.state_changed'=>'Se cambió el estado de una empresa',
        'fleet_admin.created'=>'Se creó una cuenta de administrador empresarial','platform_admin.provisioned'=>'Se provisionó el administrador global',
        'access.invitation'=>'Se generó una invitación de activación','access.activated'=>'Se activó una cuenta','user.access_changed'=>'Se cambió el acceso de una cuenta',
        'driver.created'=>'Se registró un conductor','driver.updated'=>'Se actualizó un conductor','assignment.changed'=>'Se cambió una asignación de vehículo',
        'vehicle.created'=>'Se registró un vehículo','vehicle.updated'=>'Se modificó un vehículo','document.uploaded'=>'Se registró un documento privado','document.reviewed'=>'Se revisó un documento',
        'support.created'=>'Se registró una incidencia de soporte','support.updated'=>'Se actualizaron datos de un ticket','support.comment'=>'Se añadió seguimiento a un ticket',
        'support.closed'=>'Se cerró una incidencia de soporte','support.reopened'=>'Se reabrió una incidencia de soporte','support.assigned'=>'Se cambió el responsable de atención','support.priority_changed'=>'Se cambió la prioridad de un ticket'];
    public const FIELDS=['name'=>'Nombre','trade_name'=>'Nombre comercial','full_name'=>'Nombre del conductor','email'=>'Correo','status'=>'Estado','access_status'=>'Acceso a la cuenta',
        'plate'=>'Placa','brand'=>'Marca','model'=>'Modelo','contact'=>'Contacto','vehicle_id'=>'Vehículo asociado','review_status'=>'Estado de revisión','document'=>'Documento privado',
        'password_reset'=>'Restablecimiento de contraseña (contenido oculto)','subject'=>'Asunto','category'=>'Categoría','priority'=>'Prioridad','device_id'=>'Dispositivo asociado','responsible_id'=>'Responsable de atención','comment'=>'Comentario (contenido no incluido)'];
    public const ROLES=['platform_admin'=>'Administrador global','fleet_admin'=>'Administrador empresarial','driver'=>'Conductor','member'=>'Miembro'];
    public static function description(AdministrativeAudit $a): string {return self::ACTIONS[$a->action]??'Acción histórica sin descripción catalogada';}
    public static function fields(AdministrativeAudit $a): array {
        $fields=$a->changes['fields']??[];
        return is_array($fields)?array_values(array_intersect(array_filter($fields,fn($v)=>is_string($v)),array_keys(self::FIELDS))):[];
    }
    public static function role(AdministrativeAudit $a): string {
        $snapshot=$a->changes['actor_role']??null;
        return is_string($snapshot)&&isset(self::ROLES[$snapshot])?self::ROLES[$snapshot]:((self::ROLES[$a->actor?->role??'']??'No disponible').' (rol actual, no histórico)');
    }
    public static function resource(AdministrativeAudit $a): string {return ctype_digit((string)$a->entity_id)?'#'.$a->entity_id:'Identificador no disponible';}
    public static function result(AdministrativeAudit $a): string {$value=$a->changes['result']??null;return is_string($value)?(['SUCCESS'=>'Completada','FAILED'=>'Fallida','DENIED'=>'Denegada'][$value]??'No registrado'):'No registrado';}
    public static function transitions(AdministrativeAudit $a): array {
        $out=[];$transitions=$a->changes['transitions']??[];if(!is_array($transitions))return [];foreach($transitions as $field=>$change) {
            if(!in_array($field,['status','access_status','priority','device_id','responsible_id'],true)||!is_array($change))continue;
            $values=[];foreach(['from','to'] as $side){
                $value=$change[$side]??null;
                if(in_array($field,['device_id','responsible_id'],true))$values[$side]=is_int($value)&&$value>0?'ID '.$value:($value===null?'Sin asignar':'Contenido oculto');
                else $values[$side]=self::state($value);
            }$out[$field]=$values;
        }return $out;
    }
    private static function state(mixed $value): string {
        return is_string($value)?(['ACTIVE'=>'Activo','INACTIVE'=>'Inactivo','PENDING'=>'Pendiente de activación','DISABLED'=>'Deshabilitado',
            'OPEN'=>'Abierto','IN_PROGRESS'=>'En proceso','RESOLVED'=>'Resuelto','CLOSED'=>'Cerrado','LOW'=>'Baja','MEDIUM'=>'Media','HIGH'=>'Alta'][$value]??'Contenido oculto'):'No registrado';
    }
}
