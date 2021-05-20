package com.design.结构型模式.适配器模式.类的适配器模式;

/**
 * 目标接口
 *
 */
public interface Targetable {

  /* 与原类中的方法相同，如 Source 类中的方法名称 */
  public void method1();

  /* 新类的方法 */
  public void method2();
}
