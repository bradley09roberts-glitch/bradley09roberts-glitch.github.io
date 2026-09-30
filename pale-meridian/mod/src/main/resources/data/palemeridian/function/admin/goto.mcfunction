# QA helper: /function palemeridian:admin/goto {poi:"hollin.plaza"} (names in tools/generated/poi.json)
$function palemeridian:poi/get {name:"$(poi)"}
function palemeridian:admin/_goto with storage palemeridian:tmp poi
