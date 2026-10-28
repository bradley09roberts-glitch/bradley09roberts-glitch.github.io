using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection;

namespace Fm26Moneyball
{
    /// <summary>
    /// Maps the game's own code by keyword: types and methods whose names suggest transfers, contracts,
    /// wages and negotiation. The output is what lets the real patches be written against the right names.
    /// </summary>
    public static class Discovery
    {
        const BindingFlags Decl = BindingFlags.Instance | BindingFlags.Static | BindingFlags.Public | BindingFlags.NonPublic | BindingFlags.DeclaredOnly;

        static readonly string[] SkipPrefixes =
        {
            "System", "mscorlib", "Microsoft", "Mono.", "netstandard", "UnityEngine", "Unity.", "UnityEditor",
            "BepInEx", "0Harmony", "MonoMod", "HarmonyXInterop", "Newtonsoft", "Il2Cpp", "Cpp2IL"
        };

        static readonly string[] OwnAssemblies = { "Fm26Moneyball.Core", "Fm26Moneyball.Plugin" };

        public static bool IsGameAssembly(Assembly a)
        {
            if (a.IsDynamic) return false;
            var n = a.GetName().Name;
            return !OwnAssemblies.Contains(n) && !SkipPrefixes.Any(p => n.StartsWith(p, StringComparison.OrdinalIgnoreCase));
        }

        public static List<string> Scan(IEnumerable<Assembly> assemblies, IEnumerable<string> keywords, int maxLines = 20000)
        {
            var kw = keywords.Select(k => k.Trim()).Where(k => k.Length > 0).ToArray();
            var lines = new List<string>();
            foreach (var asm in assemblies.Where(IsGameAssembly).OrderBy(a => a.GetName().Name))
            {
                var section = new List<string>();
                foreach (var t in SafeTypes(asm))
                {
                    if (lines.Count + section.Count >= maxLines) break;
                    try { DescribeType(t, kw, section); } catch { /* one odd type must not stop the scan */ }
                }
                if (section.Count == 0) continue;
                lines.Add("# Assembly: " + asm.GetName().Name);
                lines.AddRange(section);
                lines.Add("");
            }
            if (lines.Count >= maxLines) lines.Add("# truncated at " + maxLines + " lines; narrow the keywords");
            return lines;
        }

        static void DescribeType(Type t, string[] kw, List<string> output)
        {
            bool typeHit = Hit(t.FullName ?? t.Name, kw);
            var methods = t.GetMethods(Decl).Where(m => !m.IsSpecialName).ToArray();
            var chosen = typeHit ? methods.Take(200).ToArray() : methods.Where(m => Hit(m.Name, kw)).ToArray();
            if (!typeHit && chosen.Length == 0) return;

            output.Add("TYPE " + t.FullName + (t.BaseType != null && t.BaseType != typeof(object) ? " : " + t.BaseType.Name : ""));
            if (typeHit)
            {
                foreach (var f in t.GetFields(Decl).Take(60))
                    output.Add("  FIELD  " + Short(f.FieldType) + " " + f.Name);
                foreach (var p in t.GetProperties(Decl).Take(60))
                    output.Add("  PROP   " + Short(p.PropertyType) + " " + p.Name);
            }
            foreach (var m in chosen)
                output.Add("  METHOD " + (m.IsStatic ? "static " : "") + Short(m.ReturnType) + " " + m.Name + "(" +
                           string.Join(", ", m.GetParameters().Select(p => Short(p.ParameterType) + " " + p.Name)) + ")");
        }

        static bool Hit(string text, string[] kw) =>
            kw.Any(k => text.IndexOf(k, StringComparison.OrdinalIgnoreCase) >= 0);

        static string Short(Type t) => t.IsGenericType ? t.Name.Split('`')[0] + "<" + string.Join(",", t.GetGenericArguments().Select(Short)) + ">" : t.Name;

        static IEnumerable<Type> SafeTypes(Assembly a)
        {
            try { return a.GetTypes(); }
            catch (ReflectionTypeLoadException e) { return e.Types.Where(t => t != null); }
            catch { return Enumerable.Empty<Type>(); }
        }
    }
}
