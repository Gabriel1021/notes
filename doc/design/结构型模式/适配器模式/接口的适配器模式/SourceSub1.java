package com.design.结构型模式.适配器模式.接口的适配器模式;

/**
 * 继承抽象类 Wrapper2 ，间接实现 Sourceable 接口中的某个方法
 *
 */
public class SourceSub1 extends Wrapper2 {

  /**
   * 只想实现 Sourceable 接口中的 method1 方法。
   *
   *
   * @see com.zb.结构型模式.适配器模式.接口的适配器模式.Wrapper2#method1()
   */
  public void method1() {
    System.out.println("the sourceable interface's first Sub1!");
  }
}
