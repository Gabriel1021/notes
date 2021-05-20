package com.design.行为型模式.父类与子类关系.策略模式;

/**
 * 数值相减
 *
 */
public class Minus extends AbstractCalculator implements ICalculator {

  @Override
  public int calculate(String exp) {
    int arrayInt[] = split(exp, "-");
    return arrayInt[0] - arrayInt[1];
  }

}