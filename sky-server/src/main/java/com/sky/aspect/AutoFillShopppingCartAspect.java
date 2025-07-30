package com.sky.aspect;

import com.sky.annotation.AutoFillShoppingCart;
import com.sky.constant.AutoFillConstant;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 实现购物车创建时间的自动填充功能
 */
@Aspect
@Component
@Slf4j
public class AutoFillShopppingCartAspect {
    @Pointcut("execution(* com.sky.mapper.*.*(..))" +
            "&& @annotation(com.sky.annotation.AutoFillShoppingCart)")
    public void autoFillPointCut() {
    }

    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        log.info("【公共字段填充】开始");
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFillShoppingCart autoFillShoppingCart = signature.getMethod().getAnnotation(AutoFillShoppingCart.class);

        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Object entity = args[0];
        LocalDateTime now = LocalDateTime.now();
        Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
        setCreateTime.invoke(entity, now);
        log.info("【AutoFillShoppingCart公共字段填充】完毕");
    }
}

