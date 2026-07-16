param(
    [Parameter(Mandatory = $true)]
    [string]$Subject,

    [Parameter(Mandatory = $true)]
    [string]$Recipients,

    [Parameter(Mandatory = $true)]
    [string]$HtmlFile,

    [Parameter(Mandatory = $false)]
    [string]$BannerFile
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $HtmlFile)) {
    throw "No existe el archivo HTML temporal: $HtmlFile"
}

$outlook = $null
$mail = $null

try {
    $outlook = New-Object -ComObject Outlook.Application
    $mail = $outlook.CreateItem(0)

    $mail.To = $Recipients
    $mail.Subject = $Subject
    $mail.HTMLBody = [System.IO.File]::ReadAllText($HtmlFile, [System.Text.Encoding]::UTF8)

    if ($BannerFile -and (Test-Path -LiteralPath $BannerFile)) {
        $attachment = $mail.Attachments.Add($BannerFile)
        $attachment.PropertyAccessor.SetProperty('http://schemas.microsoft.com/mapi/proptag/0x3712001F', 'sipro-banner')
        $attachment.PropertyAccessor.SetProperty('http://schemas.microsoft.com/mapi/proptag/0x7FFE000B', $true)
    }

    $mail.Send()
    Write-Output 'Correo enviado con Outlook Win32.'
}
finally {
    if ($mail -ne $null) {
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($mail)
    }

    if ($outlook -ne $null) {
        [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($outlook)
    }

    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}