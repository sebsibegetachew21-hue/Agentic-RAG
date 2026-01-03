@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    https://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set MAVEN_SKIP_RC=true

set "SCRIPT_DIR=%~dp0"
if "%SCRIPT_DIR%"=="" set "SCRIPT_DIR=%CD%"

set "WRAPPER_DIR=%SCRIPT_DIR%\.mvn\wrapper"
set "PROPERTIES=%WRAPPER_DIR%\maven-wrapper.properties"

if not exist "%PROPERTIES%" (
  echo Cannot find %PROPERTIES%
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in ("%PROPERTIES%") do (
  if "%%A"=="distributionUrl" set "DISTRIBUTION_URL=%%B"
  if "%%A"=="wrapperUrl" set "WRAPPER_URL=%%B"
)

if not exist "%WRAPPER_DIR%\maven-wrapper.jar" (
  echo Downloading Maven Wrapper from: %WRAPPER_URL%
  mkdir "%WRAPPER_DIR%" 2>nul
  powershell -Command "Invoke-WebRequest -UseBasicParsing -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_DIR%\maven-wrapper.jar'"
  if errorlevel 1 (
    echo Failed to download maven-wrapper.jar
    exit /b 1
  )
)

set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%JAVA_EXE%" (
  set "JAVA_EXE=java"
)

if not exist "%JAVA_EXE%" (
  echo Java is not installed or JAVA_HOME is not set.
  exit /b 1
)

set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
if not "%MAVEN_PROJECTBASEDIR%"=="" goto skipBaseDir
set MAVEN_PROJECTBASEDIR=%SCRIPT_DIR%
:skipBaseDir

"%JAVA_EXE%" ^
  -Dmaven.multiModuleProjectDirectory="%MAVEN_PROJECTBASEDIR%" ^
  -cp "%WRAPPER_DIR%\maven-wrapper.jar" ^
  org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
