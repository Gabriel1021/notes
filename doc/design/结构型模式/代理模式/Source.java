package com.design.结构型模式.代理模式;

/**
 * 被代理类，实现 Sourceable 接口
 *
 */
public class Source implements Sourceable {

  @Override
  public void method() {
    System.out.println("执行被代理类的方法...");
  }

}
