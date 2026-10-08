; CounterCoach NSIS customisations.
;
; electron-builder checks whether the app is running before installing/uninstalling. Under Wine
; (used only to build/test the installer on Linux) PowerShell is unavailable and the tasklist
; fallback reports a false "app is running", which aborts silent installs. Skip the check only
; when a Wine registry key is present; on Windows the standard check runs unchanged.
; The stock check needs these when a custom macro is defined (the template skips them).
!include "getProcessInfo.nsh"
Var pid

!macro customCheckAppRunning
  ClearErrors
  EnumRegKey $R9 HKCU "Software\Wine" 0
  ${if} $R9 == ""
    EnumRegKey $R9 HKLM "Software\Wine" 0
  ${endif}
  ${if} $R9 == ""
    !insertmacro IS_POWERSHELL_AVAILABLE
    !insertmacro _CHECK_APP_RUNNING
  ${else}
    DetailPrint "Wine detected: skipping running-app check"
  ${endif}
!macroend
