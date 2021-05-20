package com.design.创建型模式.抽象工厂模式;

/**
 * 具体产品类 B1，继承抽象类 Product
 *
 */
public class ProductB1 extends Product {

  @Override
  public void dosomething() {
    System.out.println("这里是产品B1");
  }

}
