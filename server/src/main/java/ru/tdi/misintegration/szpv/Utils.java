package ru.tdi.misintegration.szpv;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import ru.tdi.misintegration.szpv.ws.ArrayOfError;
import ru.tdi.misintegration.szpv.ws.Error;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public class Utils {
    public static XMLGregorianCalendar toXMLDate(Date dt)  {
        try {
            if (dt == null) {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar("0001-01-01T00:00:00");
            }
            GregorianCalendar c = new GregorianCalendar();
            c.setTime(dt);
            c.set(Calendar.HOUR_OF_DAY, 12);
            c.setTimeZone(TimeZone.getTimeZone("GMT+3:00"));
            c.set(Calendar.HOUR_OF_DAY, 0);
            XMLGregorianCalendar db = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
            db.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
            db.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
            return db;
        } catch (DatatypeConfigurationException ex) {
            return null;
        }
    }

    public static XMLGregorianCalendar toXMLDateTime(Date dt)  {
        try {
            if (dt == null) {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar("0001-01-01T00:00:00");
            }
            GregorianCalendar c = new GregorianCalendar();
            c.setTime(dt);
            XMLGregorianCalendar db = DatatypeFactory.newInstance().newXMLGregorianCalendar(c);
            db.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
            db.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
            return db;
        } catch (DatatypeConfigurationException ex) {
            return null;
        }
    }

    public static XMLGregorianCalendar toXMLDateTime(String dt)  {
        try {
            if (dt == null) {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar("0001-01-01T00:00:00");
            }
            XMLGregorianCalendar db = DatatypeFactory.newInstance().newXMLGregorianCalendar(dt);
            db.setTimezone(DatatypeConstants.FIELD_UNDEFINED);
            db.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
            return db;
        } catch (DatatypeConfigurationException ex) {
            return null;
        }
    }

    public static Date fromXMLDate(XMLGregorianCalendar dt) {
        if (dt==null) return null;
        return dt.toGregorianCalendar().getTime();
    }

    public static ArrayOfError setError(Integer code, String msg) {
        ArrayOfError errArr  = new ArrayOfError();
        Error err = new Error();
        err.setErrorDescription(msg);
        err.setIdError(code);
        errArr.getError().add(err);
        return errArr;
    }

    public static String formatSNILS(String snV) {
        if (snV==null) return null;
        if (snV.trim().isEmpty()) return null;
        if (snV.trim().length()!=11) return null;
        return snV.substring(0, 3) + "-" + snV.substring(3, 6) + "-" + snV.substring(6, 9) + " " + snV.substring(9, 11);
    }

}
