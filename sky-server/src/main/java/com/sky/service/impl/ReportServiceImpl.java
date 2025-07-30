package com.sky.service.impl;

import com.sky.dto.GoodsSalesDTO;
import com.sky.dto.ReportDTO;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ReportMapper;
import com.sky.service.ReportService;
import com.sky.service.WorkspaceService;
import com.sky.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReportServiceImpl implements ReportService {
    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private WorkspaceService workspaceService;

    @Override
    public TurnoverReportVO getTurnoverStatistics(ReportDTO reportDTO) {
        // 先生成日期的字符串列表
        String dataList = getDateList(reportDTO.getBegin(), reportDTO.getEnd());
        // 先找到营业额数据
        List<Double> turnoverList = reportMapper.getTurnoverStatistics(reportDTO);
        // 再把turnoverList变成字符串
        String turnoverListStr = listToString(turnoverList);
        return new TurnoverReportVO(dataList, turnoverListStr);
    }

    /**
     * 用户信息统计
     */
    @Override
    public UserReportVO getUserStatistics(ReportDTO reportDTO) {
        String dataList = getDateList(reportDTO.getBegin(), reportDTO.getEnd());
        List<Integer> newUserList = new ArrayList<>();
        List<Integer> totalUserList = new ArrayList<>();
        LocalDate begin = reportDTO.getBegin();
        LocalDate end = reportDTO.getEnd();
        for (LocalDate time = begin; !time.isAfter(end); time = time.plusDays(1)) {
            Integer newUser = reportMapper.getNewUser(time);
            Integer totalUser = reportMapper.getTotalUser(time);
            newUserList.add(newUser);
            totalUserList.add(totalUser);
        }
        return UserReportVO.builder()
                .dateList(dataList)
                .newUserList(listToString(newUserList))
                .totalUserList(listToString(totalUserList)).build();
    }

    @Override
    public OrderReportVO getOrderStatistics(ReportDTO reportDTO) {
        LocalDate begin = reportDTO.getBegin();
        LocalDate end = reportDTO.getEnd();
        String dataList = getDateList(begin, end);

        List<Integer> orderCountList = new ArrayList<>();
        List<Integer> validOrderCountList = new ArrayList<>();
        Integer totalOrderCount = 0;
        Integer validOrderCount = 0;
        for (LocalDate time = begin; !time.isAfter(end); time = time.plusDays(1)) {
            Integer order = reportMapper.countOrder(time);
            Integer validOrder = reportMapper.countValidOrder(time);
            orderCountList.add(order);
            validOrderCountList.add(validOrder);
            totalOrderCount += order;
            validOrderCount += validOrder;
        }
        return OrderReportVO.builder()
                .dateList(dataList)
                .orderCountList(listToString(orderCountList))
                .validOrderCountList(listToString(validOrderCountList))
                .totalOrderCount(totalOrderCount)
                .validOrderCount(validOrderCount)
                .orderCompletionRate(validOrderCount * 1.0 / totalOrderCount)
                .build();

    }

    @Override
    public SalesTop10ReportVO top10(ReportDTO reportDTO) {
        List<GoodsSalesDTO> goodsSalesDTOList = reportMapper.top10(reportDTO);
        //  获取名称列表和销量列表
        String nameList = listToString(goodsSalesDTOList.stream().map(GoodsSalesDTO::getName).collect(Collectors.toList()));
        String numberList = listToString(goodsSalesDTOList.stream().map(GoodsSalesDTO::getNumber).collect(Collectors.toList()));
        return SalesTop10ReportVO.builder()
                .nameList(nameList)
                .numberList(numberList)
                .build();

    }

    /**
     * 导出营业数据
     */
    @Override
    public void exportBusinessData(HttpServletResponse httpServletResponse) {
        // 1.查询数据库，获取营业数据 —— 查询最近30天的营运数据
        LocalDate dateEnd = LocalDate.now().minusDays(1);
        LocalDate dateBegin = LocalDate.now().minusDays(30);
        BusinessDataVO businessDataVO = workspaceService.getBusinessData(LocalDateTime.of(dateBegin, LocalTime.MIN), LocalDateTime.of(dateEnd, LocalTime.MAX));

        // 2.通过POI把数据写入Excel文件中
        InputStream excel_template = this.getClass().getClassLoader().getResourceAsStream("template/运营数据报表模板.xlsx");
        if (excel_template == null) {
            throw new RuntimeException("无法获取模板文件");
        }

        try {
            XSSFWorkbook excel = new XSSFWorkbook(excel_template);
            //获取表格文件的Sheet页
            XSSFSheet sheet = excel.getSheet("Sheet1");

            //填充数据--时间
            sheet.getRow(1).getCell(1).setCellValue("时间：" + dateBegin + "至" + dateEnd);

            //获得第4行
            XSSFRow row = sheet.getRow(3);
            row.getCell(2).setCellValue(businessDataVO.getTurnover());
            row.getCell(4).setCellValue(businessDataVO.getOrderCompletionRate());
            row.getCell(6).setCellValue(businessDataVO.getNewUsers());

            //获得第5行
            row = sheet.getRow(4);
            row.getCell(2).setCellValue(businessDataVO.getValidOrderCount());
            row.getCell(4).setCellValue(businessDataVO.getUnitPrice());

            //填充明细数据
            for (int i = 0; i < 30; i++) {
                LocalDate date = dateBegin.plusDays(i);
                //查询某一天的营业数据
                BusinessDataVO businessData = workspaceService.getBusinessData(LocalDateTime.of(date, LocalTime.MIN), LocalDateTime.of(date, LocalTime.MAX));

                //获得某一行
                row = sheet.getRow(7 + i);
                row.getCell(1).setCellValue(date.toString());
                row.getCell(2).setCellValue(businessData.getTurnover());
                row.getCell(3).setCellValue(businessData.getValidOrderCount());
                row.getCell(4).setCellValue(businessData.getOrderCompletionRate());
                row.getCell(5).setCellValue(businessData.getUnitPrice());
                row.getCell(6).setCellValue(businessData.getNewUsers());
            }

            //3. 通过输出流将Excel文件下载到客户端浏览器
            ServletOutputStream out = httpServletResponse.getOutputStream();
            excel.write(out);

            //关闭资源
            out.close();
            excel.close();//获取表格文件的Sheet页

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * 把list集合变成字符串，使用逗号分割
     */
    private <T> String listToString(List<T> tList) {
        return tList.stream().map(Object::toString).collect(Collectors.joining(","));
    }

    private String getDateList(LocalDate begin, LocalDate end) {
        List<LocalDate> list = new ArrayList<>();
        list.add(begin);
        while (!begin.equals(end)) {
            begin = begin.plusDays(1);
            list.add(begin);
        }
        StringBuilder sb = new StringBuilder();
        for (LocalDate date : list) {
            sb.append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))).append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }


}
