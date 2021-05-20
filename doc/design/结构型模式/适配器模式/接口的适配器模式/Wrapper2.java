package com.design.结构型模式.适配器模式.接口的适配器模式;

/**
 * 作为中间介质，其他类继承该类，就可以实现 Sourceable接口的一个或者多个方法，而不是实现全部方法，避免代码浪费冗余 实现了 Sourceable 接口，其他类如果想实现
 * Sourceable 中的某一些方法，则可以继承该 Wrapper2，就可以解决无需全部实现 Sourceable 接口的所有方法
 */
public abstract class Wrapper2 implements Sourceable {

  public void method1() {
  }

  public void method2() {
  }
}
