package io.github.mocanjie.base.myjpa.utils;

import org.springframework.util.StringUtils;

import java.security.SecureRandom;

public class CommonUtils {


	private static SecureRandom random = new SecureRandom();

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
	

	public static String underscoreToCamelCase(String str){
		if(!StringUtils.hasText(str)) return str;
		StringBuilder sb = new StringBuilder(str.length());
		for(int i = 0;i<str.length();i++){
			char c = str.charAt(i);
			if(c=='_'){
				sb.append(Character.toUpperCase(str.charAt(i+1)));
				i++;
				continue;
			}
			sb.append(c);
		}
		return sb.toString();
	}
	
}
