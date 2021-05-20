package com.design.创建型模式.工厂方法模式;

/**
 * 工厂实现该接口，返回具体的实例对象
 *
 */
public interface Provider {

  public Sender produce();
}
