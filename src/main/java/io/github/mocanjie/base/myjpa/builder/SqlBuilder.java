package io.github.mocanjie.base.myjpa.builder;

import io.github.mocanjie.base.mycommon.pager.Pager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;
import java.sql.Connection;

public class SqlBuilder {

	protected  Logger logger = LoggerFactory.getLogger(getClass());

	public static int type = 1;

//	public static String db_schema;

	@Autowired
	private DataSource ds;

	@PostConstruct
	public void init(){
		String typeName = null;
		String version = null;
		Connection connection = null;
		try{
			connection = ds.getConnection();
			typeName = CnvSmallChr(connection.getMetaData().getDatabaseProductName());
			version = CnvSmallChr(connection.getMetaData().getDatabaseProductVersion());
//			db_schema = connection.getCatalog();
			if ("mysql".equalsIgnoreCase(typeName)) {
				type = 1;
		    }else if ("oracle".equalsIgnoreCase(typeName)) {
		    	type = 2;
		    }else if (("sqlserver".equalsIgnoreCase(typeName)) || (typeName.contains("microsoft"))) {
		    	type = 3;
		    }
			else if (("kingbasees".equalsIgnoreCase(typeName))) {
				type = 4;
			}
			else if (("postgresql".equalsIgnoreCase(typeName))) {
				type = 5;
			}
			else{
//		    	throw new Error("不支持数据库类型：" + typeName);
				logger.info("没匹对正确的数据库版本，默认使用mysql模式");
		    }
		}catch(Exception e){
			logger.error("获取数据库类型异常",e);
			throw new Error("myjpa组件加载失败");
		} finally {
			if(connection != null) {
				try {
					connection.close();
				} catch (Exception ignore){}
			}
		}
		logger.info("数据库类型: {}  版本信息:{}",typeName,version);
	}

	private static boolean isBigChr(char chr)
	  {
	    return ('@' < chr) && (chr < '[');
	  }

	private static String CnvSmallChr(String str)
	  {
	    char[] chrArry = str.toCharArray();
	    for (int i = 0; i < chrArry.length; i++) {
	      if (isBigChr(chrArry[i]))
	      {
	        int tmp24_23 = i; char[] tmp24_22 = chrArry;tmp24_22[tmp24_23] = ((char)(tmp24_22[tmp24_23] + ' '));
	      }
	    }
	    return new String(chrArry);
	  }

	public static String buildPagerSql(String sql, Pager pager){
		if(type==1 || type==4){
			return  buildMysqlPagerSql(sql, pager);
		}
		if(type==2){
			return  buildOraclePagerSql(sql, pager);
		}
		if(type==3){
			return  buildSqlServerPagerSql(sql, pager);
		}
		if(type==5){
			return  buildPgsqlPagerSql(sql, pager);
		}
		//默认
		return buildMysqlPagerSql(sql, pager);
	}


	/**
	 * sqlserver
	 * @param sql
	 * @param pager
	 * @return
	 */
	private static String buildSqlServerPagerSql(String sql,Pager pager){
		StringBuilder pagingSelect = new StringBuilder(300);
	    sql = sql.replaceFirst("^\\s*[sS][eE][lL][eE][cC][tT]\\s+", "select top " + (pager.getStartRow() + pager.getPageSize()) + " ");
	    pagingSelect.append(" select * from ( select row_number()over(order by __tc__)tempRowNumber,* from (select    __tc__=0, *  from ( ");
	    pagingSelect.append(" select top 100 percent * from ( ");
	    pagingSelect.append(sql);
	    pagingSelect.append(" )  as _sqlservertb_  ");
	    String sortColumn = pager.getSort();
	    if(StringUtils.hasText(sortColumn) && StringUtils.hasText(pager.getOrder())){
	    	pagingSelect.append(" order by " + camelCaseToUnderscore(sortColumn)  +" " + pager.getOrder());
	    }
	    pagingSelect.append(" ) t )tt )ttt where tempRowNumber > ").append(pager.getStartRow()).append(" and tempRowNumber <= ").append(pager.getStartRow() + pager.getPageSize());
	    return pagingSelect.toString();
	}

	/**
	 * mysql
	 * @param sql
	 * @param pager
	 * @return
	 */
	private static String buildPgsqlPagerSql(String sql,Pager pager){
		StringBuilder pagingSelect = new StringBuilder(300);
		pagingSelect.append(" select * from ( ");
		pagingSelect.append(sql);
		pagingSelect.append(" ) as _pgsqltb_ ");
		String sortColumn = pager.getSort();
		if(StringUtils.hasText(sortColumn) && StringUtils.hasText(pager.getOrder())){
			pagingSelect.append(" order by " + camelCaseToUnderscore(sortColumn) +" " + pager.getOrder());
		}
		pagingSelect.append(" OFFSET ").append(pager.getStartRow()).append(" LIMIT ").append(pager.getPageSize());
		return pagingSelect.toString();
	}

	/**
	 * mysql
	 * @param sql
	 * @param pager
	 * @return
	 */
	private static String buildMysqlPagerSql(String sql,Pager pager){
		StringBuilder pagingSelect = new StringBuilder(300);
		pagingSelect.append(" select * from ( ");
	    pagingSelect.append(sql);
	    pagingSelect.append(" ) as _mysqltb_ ");
	    String sortColumn = pager.getSort();
	    if(StringUtils.hasText(sortColumn) && StringUtils.hasText(pager.getOrder())){
	    	pagingSelect.append(" order by " + camelCaseToUnderscore(sortColumn) +" " + pager.getOrder());
	    }
	    pagingSelect.append(" limit ").append(pager.getStartRow()).append(",").append(pager.getPageSize());
	    return pagingSelect.toString();
	}

	/**
	 * oracle
	 * @param sql
	 * @param pager
	 * @return
	 */
	private static String buildOraclePagerSql(String sql,Pager pager){
		StringBuilder pagingSelect = new StringBuilder(300);
	    pagingSelect.append("select * from ( select row_.*, rownum rownum_userforpage from ( ");
	    pagingSelect.append(" select * from ( ");
	    pagingSelect.append(sql);
	    pagingSelect.append(" )  as _oracletb_  ");
	    String sortColumn = pager.getSort();
	    if(StringUtils.hasText(sortColumn) && StringUtils.hasText(pager.getOrder())){
	    	pagingSelect.append(" order by " + camelCaseToUnderscore(sortColumn)  +" " + pager.getOrder());
	    }
	    pagingSelect.append(" ) row_ where rownum <= ")
	    .append(pager.getStartRow() + pager.getPageSize())
	    .append(") where rownum_userforpage > ")
	    .append(pager.getStartRow());
	    return pagingSelect.toString();
	}

	public static String camelCaseToUnderscore(String str){
		if(!StringUtils.hasText(str)) return str;
		StringBuilder sb = new StringBuilder(str.length());
		for(int i = 0;i<str.length();i++){
			char c = str.charAt(i);
			if(Character.isUpperCase(c)){
				sb.append("_");
				sb.append(Character.toLowerCase(c));
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}

}
