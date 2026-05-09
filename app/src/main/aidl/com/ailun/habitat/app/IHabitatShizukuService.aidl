package com.ailun.habitat.app;

interface IHabitatShizukuService {
    void destroy() = 16777114;
    String exec(String command) = 1;
    void exit() = 2;
}
