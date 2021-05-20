package com.design.创建型模式.原型模式;

/**
 * 实现一个克隆自身的操作
 */
public class ConcretePrototype_深复制 extends Prototype_深复制 {

  private static final long serialVersionUID = 1L;

  public ConcretePrototype_深复制(String name, String sexName) {
    setName(name);
    setObj(new SerializableObject(sexName));
  }
}
