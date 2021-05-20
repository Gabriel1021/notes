package com.design.结构型模式.桥接模式;

/**
 * 实现了JDBC统一接口，这里可以比如成 mysql 的驱动连接的实现
 *
 */
public class MysqlDriver implements JdbcInterface {

  @Override
  public void connect() {
    System.out.println("mysql驱动连接mysql数据库...");
  }
}