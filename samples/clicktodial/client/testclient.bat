@echo off
if "%OS%" == "Windows_NT" setlocal
rem ---------------------------------------------------------------------------
rem Make client script for test soap service
rem ---------------------------------------------------------------------------

set REAL_PATH=%~dp0
set LIBRARY=%REAL_PATH%\lib

if exist "%JAVA_HOME%"\bin\java.exe (set _RUNJAVA="%JAVA_HOME%\bin\java") else (set _RUNJAVA=java)

rem Set SOAP Library CLASSPATH
set CLASSPATH=%CLASSPATH%;.
set CLASSPATH=%CLASSPATH%;%LIBRARY%\activation.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\FastInfoset.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\http.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jaxb-impl.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jaxb-xjc.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jaxws-rt.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jaxws-tools.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jsr181-api.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\jsr250-api.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\resolver.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\saaj-impl.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\sjsxp.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\stax-ex.jar
set CLASSPATH=%CLASSPATH%;%LIBRARY%\streambuffer.jar

set MAINCLASS=com.micromethod.sipmethod.sample.clicktodial.Click2DialClient

rem Execute Java with the applicable properties
%_RUNJAVA% %JAVA_OPTS% -cp "%CLASSPATH%" -Djava.endorsed.dirs="%LIBRARY%\endorsed"  %MAINCLASS% %*

endlocal
:end
