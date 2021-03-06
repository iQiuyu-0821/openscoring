/*
 * Copyright (c) 2013 Villu Ruusmann
 *
 * This file is part of Openscoring
 *
 * Openscoring is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Openscoring is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Openscoring.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.openscoring.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openscoring.common.EvaluationRequest;
import org.openscoring.common.EvaluationResponse;
import org.supercsv.encoder.DefaultCsvEncoder;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.prefs.CsvPreference;

public class CsvUtil {

	private CsvUtil(){
	}

	static
	public CsvPreference getFormat(String delimiterChar, String quoteChar){
		char delimiter = ',';
		char quote = '\"';

		if(delimiterChar != null){
			delimiterChar = decodeDelimiter(delimiterChar);

			if(delimiterChar.length() != 1){
				throw new IllegalArgumentException("Invalid CSV delimiter character: \"" + delimiterChar + "\"");
			}

			delimiter = delimiterChar.charAt(0);
		} // End if

		if(quoteChar != null){
			quoteChar = decodeQuote(quoteChar);

			if(quoteChar.length() != 1){
				throw new IllegalArgumentException("Invalid CSV quote character: \"" + quoteChar + "\"");
			}

			quote = quoteChar.charAt(0);
		}

		CsvPreference format = createFormat(delimiter, quote);

		return format;
	}

	static
	public CsvPreference getFormat(BufferedReader reader) throws IOException {
		reader.mark(10 * 1024);

		for(int i = 0; i < CsvUtil.DELIMITERS.length; i++){
			char delimiter = CsvUtil.DELIMITERS[i];

			try {
				CsvPreference format = createFormat(delimiter, '\"');

				if(checkFormat(reader, format)){
					return format;
				}
			} finally {
				reader.reset();
			}
		}

		throw new IOException("Unrecognized CSV format");
	}

	static
	private CsvPreference createFormat(char delimiter, char quote){
		CsvPreference.Builder builder = new CsvPreference.Builder(quote, delimiter, "\n");
		builder.useEncoder(new DefaultCsvEncoder());

		return builder.build();
	}

	static
	private boolean checkFormat(BufferedReader reader, CsvPreference format) throws IOException {
		CsvListReader parser = new CsvListReader(reader, format){

			@Override
			public void close(){
			}
		};

		int columns = 0;

		// Check the header line and the first ten lines
		for(int line = 0; line < (1 + 10); line++){
			List<String> row = parser.read();

			if(row == null){
				break;
			}

			int rowColumns = row.size();
			if((rowColumns > 1) && (columns == 0 || columns == rowColumns)){
				columns = rowColumns;
			} else

			{
				return false;
			}
		}

		parser.close();

		return (columns > 1);
	}

	static
	public Table<EvaluationRequest> readTable(BufferedReader reader, CsvPreference format) throws IOException {
		Table<EvaluationRequest> table = new Table<>();

		CsvMapReader parser = new CsvMapReader(reader, format);

		String[] header = parser.getHeader(true);

		if(header.length > 0 && ("id").equalsIgnoreCase(header[0])){
			table.setId(header[0]);
		}

		List<EvaluationRequest> requests = new ArrayList<>();

		while(true){
			Map<String, String> arguments = parser.read(header);
			if(arguments == null){
				break;
			}

			String id = arguments.remove(table.getId());

			EvaluationRequest request = new EvaluationRequest(id);
			request.setArguments(arguments);

			requests.add(request);
		}

		parser.close();

		table.setRows(requests);

		return table;
	}

	static
	public void writeTable(BufferedWriter writer, CsvPreference format, Table<EvaluationResponse> table) throws IOException {
		CsvMapWriter formatter = new CsvMapWriter(writer, format);

		String[] header = null;

		List<EvaluationResponse> responses = table.getRows();

		for(EvaluationResponse response : responses){
			Map<String, ?> result = response.getResult();

			String id = response.getId();
			if(id != null){
				result = join(Collections.<String, String>singletonMap(table.getId(), id), result);
			} // End if

			if(header == null){
				Set<String> keys = result.keySet();

				header = (keys).toArray(new String[keys.size()]);

				formatter.writeHeader(header);
			}

			formatter.write(result, header);
		}

		formatter.flush();
		formatter.close();
	}

	static
	private String decodeDelimiter(String delimiterChar){

		if(("\\t").equals(delimiterChar)){
			return "\t";
		}

		return delimiterChar;
	}

	static
	private String decodeQuote(String quoteChar){

		if(("\\'").equals(quoteChar)){
			return "\'";
		} else

		if(("\\\"").equals(quoteChar)){
			return "\"";
		}

		return quoteChar;
	}

	static
	private Map<String, ?> join(Map<String, ?> left, Map<String, ?> right){
		Map<String, Object> result = new LinkedHashMap<>(left);
		result.putAll(right);

		return result;
	}

	static
	public class Table<R> {

		private String id = null;

		private List<R> rows = null;


		public String getId(){
			return this.id;
		}

		public void setId(String id){

			if(this.id != null){
				throw new IllegalStateException();
			}

			this.id = id;
		}

		public List<R> getRows(){
			return this.rows;
		}

		public void setRows(List<R> rows){
			this.rows = rows;
		}
	}

	private static final char[] DELIMITERS = {',', ';', '\t'};
}