package com.sky.service;

import com.sky.dto.EmployeeDTO;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.result.PageResult;

import java.util.List;

public interface EmployeeService {

    /**
     * 员工登录
     *
     * @param employeeLoginDTO
     * @return
     */
    Employee login(EmployeeLoginDTO employeeLoginDTO);


    /**
     * 新增员工
     *
     * @param employeeDTO
     */
    void save(EmployeeDTO employeeDTO);

    /**
     * 分页查询
     */
    PageResult page(EmployeePageQueryDTO employeePageQueryDTO);

    /**
     * 启动/禁用员工账号
     */
    void startOrStop(Integer status, Long id);

    /**
     * 根据id查询员工
     */
    Employee getById(Long id);

    /**
     * 修改员工信息
     */
    void update(EmployeeDTO employeeDTO);

    /**
     * 删除员工信息
     */
    void deleteByIds(List<Long> ids);
}
