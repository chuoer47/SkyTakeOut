package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.annotation.AutoFill;
import com.sky.dto.EmployeePageQueryDTO;
import com.sky.entity.Employee;
import com.sky.enumeration.OperationType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EmployeeMapper {

    /**
     * 根据用户名查询员工
     * @param username
     * @return
     */
    @Select("select * from employee where username = #{username}")
    Employee getByUsername(String username);
    @AutoFill(value = OperationType.INSERT)
    void insert(Employee employee);

    Page<Employee> page(EmployeePageQueryDTO employeePageQueryDTO);

    Employee getById(Long id);

    @AutoFill(value = OperationType.UPDATE)
    void update(Employee employee);

    void deleteByIds(List<Long> ids);
}
