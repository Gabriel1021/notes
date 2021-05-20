package com.design.行为型模式.通过中间类.访问者模式;

/**
 * 存放要访问的对象
 *
 */
public interface Visitor {

  public void visit(Subject sub);
}