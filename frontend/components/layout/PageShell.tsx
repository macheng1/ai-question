import { Header } from "./Header";
import { Footer } from "./Footer";

export function PageShell({children}:{children:React.ReactNode}){return <div className="flex min-h-screen flex-col"><Header/><div className="flex-1">{children}</div><Footer/></div>}
