@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-18.0.1.1
call gradlew.bat :services:validation-service:bootRun
