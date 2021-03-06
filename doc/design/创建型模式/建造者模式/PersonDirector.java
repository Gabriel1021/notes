package com.design.创建型模式.建造者模式;

/**
 * 接收建造对象，建造一个具体的对象
 *
 */
public class PersonDirector {

  /**
   * 接收实现 PersonBuilder 接口的建造对象。 调用建造对象的建造方法，返回最终的建造出来的对象
   *
   */
  public Person constructPerson(PersonBuilder pb) {
    pb.buildHead();
    pb.buildBody();
    pb.buildFoot();
    return pb.buildPerson();
  }
}