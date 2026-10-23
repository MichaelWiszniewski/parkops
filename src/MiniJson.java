import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON codec for supplied infrastructure. It does NOT validate tool authority. */
public final class MiniJson {
    private MiniJson() {}
    public static Object parse(String text) {
        if(text==null || text.length()>1_048_576) throw new IllegalArgumentException("JSON missing or exceeds 1 MiB characters");
        Parser p=new Parser(text); Object result=p.value(0); p.ws();
        if(p.at!=text.length()) throw p.error("trailing input");
        return result;
    }
    public static Map<String,Object> object(Object value) {
        if(!(value instanceof Map<?,?> map)) throw new IllegalArgumentException("JSON object required");
        Map<String,Object> result=new LinkedHashMap<>();
        for(var entry:map.entrySet()) {
            if(!(entry.getKey() instanceof String key)) throw new IllegalArgumentException("string key required");
            result.put(key,entry.getValue());
        }
        return result;
    }
    public static String stringify(Object value) { StringBuilder out=new StringBuilder(); encode(value,out,0); return out.toString(); }
    private static void encode(Object v,StringBuilder out,int depth) {
        if(depth>64) throw new IllegalArgumentException("JSON nesting too deep");
        if(v==null) { out.append("null");return; }
        if(v instanceof String s) {
            out.append('"');
            for(int i=0;i<s.length();i++) { char c=s.charAt(i);switch(c) {
                case '"' -> out.append("\\\"");case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");case '\r' -> out.append("\\r");case '\t' -> out.append("\\t");
                default -> { if(c<32) out.append(String.format("\\u%04x",(int)c));else out.append(c); }
            }}
            out.append('"');return;
        }
        if(v instanceof Boolean) {out.append(v);return;}
        if(v instanceof Number n) {
            String s=n.toString();
            if(!s.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IllegalArgumentException("finite JSON number required");
            out.append(s);return;
        }
        if(v instanceof Map<?,?> m) {
            out.append('{');boolean first=true;
            for(var e:m.entrySet()) {
                if(!(e.getKey() instanceof String)) throw new IllegalArgumentException("string map key required");
                if(!first)out.append(',');first=false;encode(e.getKey(),out,depth+1);out.append(':');encode(e.getValue(),out,depth+1);
            }out.append('}');return;
        }
        if(v instanceof List<?> list) {
            out.append('[');boolean first=true;
            for(Object item:list) {if(!first)out.append(',');first=false;encode(item,out,depth+1);}out.append(']');return;
        }
        throw new IllegalArgumentException("Unsupported JSON value "+v.getClass().getName());
    }
    private static final class Parser {
        private final String s;private int at;
        Parser(String s) {this.s=s;}
        IllegalArgumentException error(String why) {return new IllegalArgumentException(why+" at JSON offset "+at);}
        void ws() {while(at<s.length() && " \r\n\t".indexOf(s.charAt(at))>=0)at++;}
        boolean take(char c) {ws();if(at<s.length() && s.charAt(at)==c){at++;return true;}return false;}
        void need(char c) {if(!take(c))throw error("expected "+c);}
        Object value(int depth) {
            if(depth>64)throw error("nesting too deep");ws();if(at>=s.length())throw error("missing value");
            char c=s.charAt(at);
            if(c=='"')return string();
            if(c=='{') {
                at++;Map<String,Object> map=new LinkedHashMap<>();if(take('}'))return map;
                do {ws();if(at>=s.length() || s.charAt(at)!='"')throw error("object key required");String key=string();need(':');if(map.containsKey(key))throw error("duplicate key");map.put(key,value(depth+1));}while(take(','));
                need('}');return map;
            }
            if(c=='[') {at++;List<Object> list=new ArrayList<>();if(take(']'))return list;do{list.add(value(depth+1));}while(take(','));need(']');return list;}
            if(s.startsWith("true",at)){at+=4;return true;}if(s.startsWith("false",at)){at+=5;return false;}if(s.startsWith("null",at)){at+=4;return null;}
            int start=at;
            while(at<s.length() && "0123456789+-.eE".indexOf(s.charAt(at))>=0)at++;
            String token=s.substring(start,at);
            if(!token.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"))throw error("invalid value or number");
            try{return new BigDecimal(token);}catch(NumberFormatException ex){throw error("invalid number");}
        }
        String string() {
            need('"');StringBuilder out=new StringBuilder();
            while(at<s.length()) {
                char c=s.charAt(at++);if(c=='"')return out.toString();
                if(c<32)throw error("unescaped control character");
                if(c!='\\'){out.append(c);continue;}
                if(at>=s.length())throw error("unfinished escape");char e=s.charAt(at++);
                switch(e) {
                    case '"','\\','/' -> out.append(e);
                    case 'b' -> out.append('\b');case 'f' -> out.append('\f');case 'n' -> out.append('\n');case 'r' -> out.append('\r');case 't' -> out.append('\t');
                    case 'u' -> {if(at+4>s.length())throw error("short Unicode escape");String hex=s.substring(at,at+4);if(!hex.matches("[0-9a-fA-F]{4}"))throw error("bad Unicode escape");out.append((char)Integer.parseInt(hex,16));at+=4;}
                    default -> throw error("unknown escape");
                }
            }
            throw error("unterminated string");
        }
    }
}
