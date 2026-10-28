using System;
using System.Collections;
using System.Linq;
using System.Reflection;
using System.Text;

namespace Fm26Moneyball
{
    /// <summary>
    /// Turns an unknown game object into a one-line description of its simple values (numbers, strings, enums),
    /// so the trace log shows which field holds the fee, the wage and so on.
    /// </summary>
    public static class ObjectDumper
    {
        const BindingFlags All = BindingFlags.Instance | BindingFlags.Public | BindingFlags.NonPublic;

        public static string Describe(object o, int maxMembers = 24, int maxText = 80)
        {
            if (o == null) return "null";
            var t = o.GetType();
            if (IsSimple(t)) return Format(o, maxText);

            var sb = new StringBuilder(t.Name);
            var coll = o as ICollection;
            if (coll != null) return sb.Append("[Count=").Append(coll.Count).Append(']').ToString();

            sb.Append('{');
            int n = 0;
            foreach (var f in t.GetFields(All))
            {
                if (n >= maxMembers) break;
                if (!IsSimple(f.FieldType)) continue;
                if (Append(sb, ref n, Clean(f.Name), () => f.GetValue(o), maxText)) { }
            }
            foreach (var p in t.GetProperties(All))
            {
                if (n >= maxMembers) break;
                if (p.GetIndexParameters().Length > 0 || !p.CanRead || !IsSimple(p.PropertyType)) continue;
                Append(sb, ref n, p.Name, () => p.GetValue(o, null), maxText);
            }
            if (n == 0) sb.Append("no simple members");
            return sb.Append('}').ToString();
        }

        static bool Append(StringBuilder sb, ref int n, string name, Func<object> read, int maxText)
        {
            string text;
            try { text = Format(read(), maxText); }
            catch { return false; }
            if (n > 0) sb.Append(", ");
            sb.Append(name).Append('=').Append(text);
            n++;
            return true;
        }

        static bool IsSimple(Type t) =>
            t.IsPrimitive || t.IsEnum || t == typeof(string) || t == typeof(decimal);

        static string Format(object v, int maxText)
        {
            if (v == null) return "null";
            var s = Convert.ToString(v, System.Globalization.CultureInfo.InvariantCulture) ?? "";
            return s.Length > maxText ? s.Substring(0, maxText) + "…" : s;
        }

        // "<Fee>k__BackingField" -> "Fee"
        static string Clean(string name) =>
            name.StartsWith("<", StringComparison.Ordinal) && name.Contains(">") ? name.Substring(1, name.IndexOf('>') - 1) : name;
    }
}
