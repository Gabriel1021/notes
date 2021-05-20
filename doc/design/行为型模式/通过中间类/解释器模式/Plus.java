package com.design.行为型模式.通过中间类.解释器模式;

/**
 * 用来计算的实现
 *
 */
public class Plus implements Expression {

  @Override
  public int interpret(Context context) {
    return context.getNum1() + context.getNum2();
  }
}