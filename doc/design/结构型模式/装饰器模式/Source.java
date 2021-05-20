package com.design.结构型模式.装饰器模式;

/**
 * 被装饰类，实现 Sourceable 接口
 *
 */
public class Source implements Sourceable {

  @Override
  public void method() {
    System.out.println("执行被装饰类的方法...");
  }

}
