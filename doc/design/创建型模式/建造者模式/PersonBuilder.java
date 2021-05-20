package com.design.创建型模式.建造者模式;

/**
 * 为创建一个对象的各个部件指定抽象接口
 *
 */
public interface PersonBuilder {

  void buildHead();

  void buildBody();

  void buildFoot();

  Person buildPerson();
}