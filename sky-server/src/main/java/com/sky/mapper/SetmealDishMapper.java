package com.sky.mapper;

import com.sky.entity.SetmealDish;
import com.sky.vo.SetmealVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    List<Long> getSetmealByDishIds(List<Long> dishIds);


    /**
     * 批量插入套餐和菜品的关联关系
     *
     * @param setmealDishes
     */
    void insertBatch(List<SetmealDish> setmealDishes);


    List<SetmealDish> getSetmealsDishById(Long id);

    /**
     * 根据套餐id删除套餐和菜品的关联关系
     *
     * @param setmealId
     */
    void deleteBySetmealId(Long setmealId);

    /**
     * 批量删除套餐和菜品的关联关系
     *
     * @param ids
     */
    void deleteBySetmealIds(List<Long> setmealIds);
}
