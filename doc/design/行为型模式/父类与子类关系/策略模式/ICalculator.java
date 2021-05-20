package com.design.行为型模式.父类与子类关系.策略模式;

/**
 * 计算接口，每个计算类型都实现该接口
 *
 */
public interface ICalculator {

  public int calculate(String exp);
}