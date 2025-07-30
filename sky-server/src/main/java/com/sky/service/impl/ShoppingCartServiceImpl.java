package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.Dish;
import com.sky.entity.Setmeal;
import com.sky.entity.ShoppingCart;
import com.sky.exception.ShoppingCartDataError;
import com.sky.mapper.DishMapper;
import com.sky.mapper.SetmealMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.service.ShoppingCartService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.sky.constant.MessageConstant.SHOPPING_CART_DATA_ERROR;

@Service
@Slf4j
public class ShoppingCartServiceImpl implements ShoppingCartService {
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private DishMapper dishMapper;
    @Autowired
    private SetmealMapper setmealMapper;

    /**
     * 添加购物车
     *
     * @param shoppingCartDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 判断当前购物车的商品是否存在
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        // 如果已经存在，只需要把数量+1
        if (shoppingCartList != null && shoppingCartList.size() > 0) {
            ShoppingCart shoppingCartOnly = shoppingCartList.get(0);
            shoppingCartOnly.setNumber(shoppingCartOnly.getNumber() + 1);
            shoppingCartMapper.updateNumberById(shoppingCartOnly);
            return;
        }
        // 不存在插入一条
        Long dishId = shoppingCartDTO.getDishId();
        Long setmealId = shoppingCartDTO.getSetmealId();
        if (dishId != null) {
            Dish dish = dishMapper.getById(dishId);
            shoppingCart.setName(dish.getName());
            shoppingCart.setImage(dish.getImage());
            shoppingCart.setAmount(dish.getPrice());
        } else if (setmealId != null) {
            Setmeal setmeal = setmealMapper.getById(setmealId);
            shoppingCart.setName(setmeal.getName());
            shoppingCart.setImage(setmeal.getImage());
            shoppingCart.setAmount(setmeal.getPrice());
        } else {
            throw new ShoppingCartDataError(SHOPPING_CART_DATA_ERROR);
        }

        shoppingCart.setNumber(1);
        shoppingCart.setCreateTime(LocalDateTime.now());

        shoppingCartMapper.insert(shoppingCart);
    }

    /**
     * @return
     */
    @Override
    public List<ShoppingCart> list() {
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        return shoppingCartList;
    }

    /**
     *
     */
    @Override
    public void clean() {
        Long userId = BaseContext.getCurrentId();
        shoppingCartMapper.deleteByUserId(userId);
    }

    /**
     * @param shoppingCartDTO
     */
    @Override
    public void subShoppingCart(ShoppingCartDTO shoppingCartDTO) {
        // 判断当前购物车的商品是否存在
        ShoppingCart shoppingCart = new ShoppingCart();
        BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);

        shoppingCart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        // 如果已经存在，只需要把数量-1
        if (shoppingCartList != null && shoppingCartList.size() > 0) {
            ShoppingCart shoppingCartOnly = shoppingCartList.get(0);
            Integer number = shoppingCartOnly.getNumber();
            if (number - 1 <= 0) {
                shoppingCartMapper.deleteById(shoppingCartOnly.getId());
            } else {
                shoppingCartOnly.setNumber(number - 1);
                shoppingCartMapper.updateNumberById(shoppingCartOnly);
            }
        } else {
            throw new ShoppingCartDataError(SHOPPING_CART_DATA_ERROR);
        }
    }

}
