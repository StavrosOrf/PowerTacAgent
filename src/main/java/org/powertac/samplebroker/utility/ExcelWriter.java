package org.powertac.samplebroker.utility;

import java.io.FileOutputStream;
import java.io.IOException;
 
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
 
/**
 * A very simple program that writes some data to an Excel file
 * using the Apache POI library.
 */
public class ExcelWriter {
	XSSFWorkbook workbook;
	XSSFSheet sheet;
	String fileName;
	FileOutputStream outputStream;
	 
	public ExcelWriter(String fileName) {
		workbook = new XSSFWorkbook();
        sheet = workbook.createSheet( fileName);
        this.fileName = fileName;
        Row rowC = sheet.createRow(0);
        Cell cellC = rowC.createCell(2);
		cellC.setCellValue("Predicted Demand KWh same hour");
		cellC = rowC.createCell(3);
		cellC.setCellValue("1 hours before");
		cellC = rowC.createCell(1);
		cellC.setCellValue("Actual Demand KWh");
		
		for(int i = 0; i< 23; i++) {
			cellC = rowC.createCell(i+4);
			cellC.setCellValue((i+2)+" hours before prediction");
		}
		
		cellC = rowC.createCell(28);
		cellC.setCellValue("Actual Consumption KWh");
		
		
        try{
        	outputStream = new FileOutputStream("log\\predictor\\"+fileName+ ".logs.xlsx");
            workbook.write(outputStream);
        }catch (Exception e) {
			System.out.println(e);
		}
	}
	
	public void writeCell(int row , int column,double value,boolean save) {
		Row rowC = sheet.getRow(row);
		
		if (rowC == null) {
			rowC = sheet.createRow(row);
		}
		
		Cell cellC = rowC.createCell(column);
		cellC.setCellValue(value);
		
        try{
        	if(save) {
        		outputStream = new FileOutputStream("log\\predictor\\"+fileName+ ".logs.xlsx");
        		workbook.write(outputStream);
        	}
        }catch (Exception e) {
			System.out.println(e);
		}
	}
	
	
	
 
    public static void main(String[] args) throws IOException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("JavaBooks");
         
        Object[][] bookData = {
                {"Head First Java", "Kathy Serria", 79},
                {"Effective Java", "Joshua Bloch", 36},
                {"Clean Code", "Robert martin", 42},
                {"Thinking in Java", "Bruce Eckel", 35},
        };
 
        int rowCount = 3;
         
        for (Object[] aBook : bookData) {
            Row row = sheet.createRow(++rowCount);
             
            int columnCount = 3;
             
            for (Object field : aBook) {
                Cell cell = row.createCell(++columnCount);
                if (field instanceof String) {
                    cell.setCellValue((String) field);
                } else if (field instanceof Integer) {
                    cell.setCellValue((Integer) field);   
                }
            }
            
            Cell cell = row.createCell(columnCount - 1);
            cell.setCellValue((Integer) 9); 
             
        }
         
        Row row = sheet.createRow(0);
        Cell cell = row.createCell(0);
        cell.setCellValue(13);
        
        try (FileOutputStream outputStream = new FileOutputStream("log\\predictor\\"+"JavaBooks.xlsx")) {
            workbook.write(outputStream);
        }catch (Exception e) {
			System.out.println(e);
		}
        
        
        
        workbook.close();
    }
 
}
