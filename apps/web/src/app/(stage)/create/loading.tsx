import { BloomLoader } from "@/components/brand/BloomLoader";
import { StageNav } from "@/components/nav/StageNav";

export default function CreateLoading() {
  return (
    <div className="flex min-h-dvh flex-col">
      <StageNav section="Create" backHref="/" backLabel="Home" />
      <div className="grid flex-1 place-items-center p-6">
        <BloomLoader size={132} label="Taking shape" />
      </div>
    </div>
  );
}
