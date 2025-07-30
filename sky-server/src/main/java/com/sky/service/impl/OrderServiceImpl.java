package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartDataError;
import com.sky.exception.UserNotFoundException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.websocket.WebSocketServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private WebSocketServer webSocketServer;


    /**
     * 提交订单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 处理各种业务异常（地址为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        Long userId = BaseContext.getCurrentId();
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(ShoppingCart.builder().userId(userId).build());
        if (shoppingCartList.isEmpty()) {
            throw new ShoppingCartDataError(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        User user = userMapper.getById(userId);
        if (user == null) {
            throw new UserNotFoundException(MessageConstant.USER_NOT_FOUND);
        }

        // 向订单插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());
        orders.setUserId(BaseContext.getCurrentId());
        orders.setAddress(addressBook.getDetail());
        orders.setUserName(user.getName());

        orderMapper.insert(orders);

        ArrayList<OrderDetail> orderDetailArrayList = new ArrayList<>();
        // 向订单明细插入n条数据
        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);
            orderDetail.setOrderId(orders.getId());
            orderDetail.setImage(cart.getImage());

            orderDetailArrayList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailArrayList);

        // 清空当前用户的购物车数据
        shoppingCartMapper.deleteByUserId(userId);

        // 封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        /*JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }*/

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");
        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        //为替代微信支付成功后的数据库订单状态更新，多定义一个方法进行修改
        Integer OrderPaidStatus = Orders.PAID; //支付状态，已支付
        Integer OrderStatus = Orders.TO_BE_CONFIRMED;  //订单状态，待接单

        //发现没有将支付时间 check_out属性赋值，所以在这里更新
        LocalDateTime check_out_time = LocalDateTime.now();

        //获取订单号码
        String orderNumber = ordersPaymentDTO.getOrderNumber();

        log.info("调用updateStatus，用于替换微信支付更新数据库状态的问题");
        orderMapper.updateStatus(OrderStatus, OrderPaidStatus, check_out_time, orderNumber);

        // 通过websocket向客户端浏览器推送消息 type,orderId,content
        log.info("通过websocket向客户端浏览器推送消息 type,orderId,content");
        Orders ordersDB = orderMapper.getByNumber(orderNumber);
        Map map = new HashMap();
        map.put("type", 1);
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + ordersDB.getNumber() + "支付成功");
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

        return vo;
    }


    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        ordersDB.setStatus(Orders.TO_BE_CONFIRMED);
        ordersDB.setCheckoutTime(LocalDateTime.now());
        ordersDB.setStatus(Orders.PAID);

//        Orders orders = Orders.builder()
//                .id(ordersDB.getId())
//                .status(Orders.TO_BE_CONFIRMED)
//                .payStatus(Orders.PAID)
//                .checkoutTime(LocalDateTime.now())
//                .packAmount(ordersDB.getPackAmount())
//                .tablewareNumber(ordersDB.getTablewareNumber())
//                .build();

        orderMapper.update(ordersDB);

        // 通过websocket向客户端浏览器推送消息 type,orderId,content
        Map map = new HashMap();
        map.put("type", 1);
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号：" + ordersDB.getNumber() + "支付成功");
        webSocketServer.sendToAllClient(JSON.toJSONString(map));

    }

    /**
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PageResult historyOrders(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());

        Page<Orders> page = orderMapper.page(ordersPageQueryDTO);
        List<OrderVO> orderVOList = new ArrayList<>();
        if (!page.isEmpty()) {
            for (Orders order : page) {
                Long OrderId = order.getId();
                List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(OrderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                orderVO.setOrderDetailList(orderDetailList);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * @param id
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO orderDetail(Long id) {
        OrderVO orderVO = new OrderVO();
        Orders orders = orderMapper.getById(id);
        BeanUtils.copyProperties(orders, orderVO);
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);
        orderVO.setOrderDetailList(orderDetailList);
        return orderVO;
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        // 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        Integer status = orders.getStatus();
        if (status > Orders.TO_BE_CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (status.equals(Orders.TO_BE_CONFIRMED)) {
            log.info("订单金额退还：{}", orders.getNumber());
        }
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(MessageConstant.USER_CANCEL_ORDER);
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 根据orderId再来一单
     *
     * @param orderId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void repetition(Long orderId) {
        // 再来一单就是将原订单中的商品重新加入到购物车中

        // 1.获取原本订单&用户ID
        Orders orders = orderMapper.getById(orderId);
        Long userId = BaseContext.getCurrentId();

        // 2.清空当前用户的购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 3.把原本订单的购物车数据重新加入购物车（购物车的数据量不大）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orderId);
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(orderDetail, shoppingCart);
            shoppingCart.setUserId(userId);
            shoppingCartMapper.insert(shoppingCart);
        }

    }

    /**
     * admin订单搜索
     *
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult conditionSearch(OrdersPageQueryDTO ordersPageQueryDTO) {
        // 1.设置分页
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        // 2.查询全部订单
        Page<Orders> page = orderMapper.page(ordersPageQueryDTO);

        // 3.遍历完成
        List<OrderVO> orderVOList = new ArrayList<>();
        if (!page.isEmpty()) {
            for (Orders order : page) {
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(order, orderVO);
                String orderDishesStr = getOrderDishesStr(order);
                orderVO.setOrderDishes(orderDishesStr);
                orderVOList.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 统计订单数据
     *
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderStatisticsVO getOrderStatisticsVO() {

        OrderStatisticsVO orderStatisticsVO = new OrderStatisticsVO();
        orderStatisticsVO.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        orderStatisticsVO.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
        orderStatisticsVO.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
        return orderStatisticsVO;
    }

    /**
     * @param id
     */
    @Override
    public void confirmByOrderId(OrdersConfirmDTO ordersConfirmDTO) {
        Orders newOrders = new Orders();
        newOrders.setId(ordersConfirmDTO.getId());
        newOrders.setStatus(Orders.CONFIRMED);

        orderMapper.update(newOrders);
    }

    /**
     * 订单拒绝
     *
     * @param ordersRejectionDTO
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrdersRejectionDTO ordersRejectionDTO) {

        Orders orders = orderMapper.getById(ordersRejectionDTO.getId());
        if (!Objects.equals(orders.getStatus(), Orders.TO_BE_CONFIRMED)) {
            // 只有订单处于“待接单”状态时可以执行拒单操作
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        // 退钱
        log.info("用户已经付钱，【办理退款】");
        // 设置拒单属性
        BeanUtils.copyProperties(ordersRejectionDTO, orders);
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders);
    }

    /**
     * 管理员取消订单
     *
     * @param ordersRejectionDTO
     */
    @Override
    public void cancelFromAdmin(OrdersCancelDTO ordersCancelDTO) {
        Orders orders = orderMapper.getById(ordersCancelDTO.getId());
        Integer status = orders.getStatus();
        if (status < Orders.CONFIRMED) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        if (status >= Orders.TO_BE_CONFIRMED) {
            log.info("订单金额退还：{}", orders.getNumber());
        }
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason(ordersCancelDTO.getCancelReason());
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }

    /**
     * 派送订单
     *
     * @param id
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delivery(Long id) {
        Orders orders = orderMapper.getById(id);
        Integer status = orders.getStatus();
        if (!Objects.equals(status, Orders.CONFIRMED)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orders.setStatus(Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(orders);
    }

    @Override
    public void completeFromAdmin(Long id) {
        Orders orders = orderMapper.getById(id);
        Integer status = orders.getStatus();
        if (!Objects.equals(status, Orders.DELIVERY_IN_PROGRESS)) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        orders.setStatus(Orders.COMPLETED);
        orderMapper.update(orders);
    }

    /**
     * 用户催单
     *
     * @param id
     */
    @Override
    public void reminderFromUser(Long id) {
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        Map map = new HashMap();
        map.put("type", 2);
        map.put("orderId", id);
        map.put("content", "订单号：" + id + "，用户催单了");
        webSocketServer.sendToAllClient(JSON.toJSONString(map));
        return;
    }


    /**
     * 根据订单id获取菜品信息字符串
     *
     * @param orders
     * @return
     */

    private String getOrderDishesStr(Orders orders) {
        // 查询订单菜品详情信息（订单中的菜品和数量）
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将每一条订单菜品信息拼接为字符串（格式：宫保鸡丁*3；）
        List<String> orderDishList = orderDetailList.stream().map(x -> {
            String orderDish = x.getName() + "*" + x.getNumber() + ";";
            return orderDish;
        }).collect(Collectors.toList());

        // 将该订单对应的所有菜品信息拼接在一起
        return String.join("", orderDishList);
    }


}
