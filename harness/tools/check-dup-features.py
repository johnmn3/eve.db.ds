import sys,re
def scan(path):
    s=open(path).read(); n=len(s); hits=[]
    def skip_ws(i):
        while i<n:
            if s[i] in ' \t\r\n,': i+=1
            elif s[i]==';':
                while i<n and s[i]!='\n': i+=1
            else: break
        return i
    def elem_end(i):
        # return end index of the element starting at i (after skip_ws)
        c=s[i]
        if c=='"':
            i+=1
            while s[i]!='"':
                i+=2 if s[i]=='\\' else 1
            return i+1
        if c=='\\':
            j=i+2
            while j<n and s[j] not in ' \t\r\n,()[]{}";': j+=1
            return j
        if c in '([{':
            close={'(' :')','[':']','{':'}'}[c]; i+=1
            while True:
                i=skip_ws(i)
                if s[i] in ')]}': return i+1
                i=elem_end(i)
        if c=='#' and i+1<n and s[i+1] in '?@\'_(^{"':
            # reader macro prefix: consume prefix chars then the element
            j=i+1
            while j<n and s[j] in '?@': j+=1
            if s[j] in '({["': return elem_end(skip_ws(j))
            return elem_end(skip_ws(j))
        if c=='^':
            e1=elem_end(skip_ws(i+1)); return elem_end(skip_ws(e1))
        if c in "'`~@":
            return elem_end(skip_ws(i+1))
        j=i
        while j<n and s[j] not in ' \t\r\n,()[]{}";': j+=1
        return max(j,i+1)
    for m in re.finditer(r'#\?@?\(',s):
        start=m.end()-1
        # skip if inside string/comment crude: check line prefix for ';'
        ls=s.rfind('\n',0,m.start())+1
        if ';' in s[ls:m.start()] : continue
        i=start+1; k=0; keys={}
        try:
            while True:
                i=skip_ws(i)
                if s[i]==')': break
                e=elem_end(i)
                if k%2==0:
                    key=s[i:e]
                    if key in keys: hits.append((s.count('\n',0,i)+1,key,keys[key]))
                    keys[key]=s.count('\n',0,i)+1
                k+=1; i=e
        except IndexError:
            hits.append((s.count('\n',0,m.start())+1,'PARSE-ERROR',0))
    return hits
bad=0
for p in sys.argv[1:]:
    for line,key,first in scan(p):
        print(f'{p}:{line}: duplicate feature {key} (first at line {first})'); bad+=1
print('dupfeat: files',len(sys.argv)-1,'hits',bad); sys.exit(1 if bad else 0)
