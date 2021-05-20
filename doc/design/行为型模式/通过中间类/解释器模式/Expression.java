package com.design.行为型模式.通过中间类.解释器模式;

/**
 * 计算的接口
 *
 */
public interface Expression {

  public int interpret(Context context);
}