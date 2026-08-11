cmake_minimum_required(VERSION 3.22.1)

project(plcserial C)

add_library(plcserial SHARED serial_port_config.c)

find_library(log-lib log)

target_link_libraries(plcserial ${log-lib})
