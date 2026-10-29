# Сценарий Г4 без IntelliJ HTTP Client: создал → двое вошли → один исключён → запуск.
# Нужны запущенные Redis (infra/local), user-service (:8082) и game-service (:8081).
#
# Запуск из папки game-service:
#   powershell -ExecutionPolicy Bypass -File http\rooms.ps1
# Работает и в Windows PowerShell 5.1, и в PowerShell 7.
# Файл сохранён в UTF-8 с BOM: без BOM Windows PowerShell 5.1 прочитал бы кириллицу неверно.

$ErrorActionPreference = 'Stop'
$UserService = 'http://localhost:8082'
$GameService = 'http://localhost:8081'

# Один запрос: печатает статус и тело, возвращает тело как объект (или $null).
# Ошибки 4xx/5xx не прерывают сценарий: часть шагов как раз проверяет отказы.
function Send-Request {
    param(
        [string]$Title,
        [string]$Method,
        [string]$Url,
        [string]$Token,
        [object]$Body
    )
    Write-Host ''
    Write-Host "### $Title" -ForegroundColor Cyan
    Write-Host "$Method $Url" -ForegroundColor DarkGray

    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{ Method = $Method; Uri = $Url; Headers = $headers; UseBasicParsing = $true }
    if ($null -ne $Body) {
        # Тело отправляем байтами UTF-8: иначе PowerShell 5.1 испортит кириллицу
        $params['Body'] = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Compress))
        $params['ContentType'] = 'application/json; charset=utf-8'
    }

    try {
        $response = Invoke-WebRequest @params
        $status = [int]$response.StatusCode
        $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    } catch {
        if (-not $_.Exception.Response) { throw }   # сервис не запущен — дальше идти нет смысла
        $status = [int]$_.Exception.Response.StatusCode
        $text = $_.ErrorDetails.Message
    }

    $color = if ($status -lt 400) { 'Green' } else { 'Yellow' }
    Write-Host "-> $status" -ForegroundColor $color
    if ($text) {
        Write-Host $text
        return $text | ConvertFrom-Json
    }
    return $null
}

function Login([string]$Name) {
    return Send-Request "Гостевой вход: $Name" POST "$UserService/api/auth/guest" -Body @{ name = $Name }
}

$anna = Login 'Анна'
$boris = Login 'Борис'
$vera = Login 'Вера'

$room = Send-Request 'Анна создаёт комнату — 201 и код' POST "$GameService/api/game/rooms" $anna.token `
    @{ caseId = 'demo'; language = 'ru'; maxPlayers = 6; turnTimeoutSeconds = 60 }
$code = $room.code
$rooms = "$GameService/api/game/rooms/$code"

Send-Request 'Борис входит в комнату — 204' POST "$rooms/join" $boris.token | Out-Null
Send-Request 'Вера входит в комнату — 204' POST "$rooms/join" $vera.token | Out-Null
Send-Request 'Сведения о комнате — players: 3' GET $rooms $vera.token | Out-Null
Send-Request 'Борис пытается начать — 403 NOT_HOST' POST "$rooms/start" $boris.token | Out-Null
Send-Request 'Анна исключает Бориса — 204' POST "$rooms/kick" $anna.token @{ playerId = $boris.user.id } | Out-Null
Send-Request 'Борис пытается вернуться — 403 KICKED_FROM_ROOM' POST "$rooms/join" $boris.token | Out-Null
Send-Request 'Анна запускает игру — 204' POST "$rooms/start" $anna.token | Out-Null
Send-Request 'Сведения — players: 2, status: IN_GAME' GET $rooms $anna.token | Out-Null

Write-Host ''
Write-Host "Готово. Код комнаты: $code (в Redis: GET room:$code)" -ForegroundColor Cyan
