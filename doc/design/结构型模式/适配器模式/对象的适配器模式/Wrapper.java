package com.design.结构型模式.适配器模式.对象的适配器模式;

/**
 * 不继承Source类，而是持有Source类的实例
 *
 */
public class Wrapper implements Targetable {

  private Source source;

  public Wrapper(Source source) {
    super();
    this.source = source;
  }

  @Override
  public void method2() {
    System.out.println("这是一个目标的方法。");
  }

  @Override
  public void method1() {
    source.method1();
  }

}
