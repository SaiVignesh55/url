import { useQuery } from "@tanstack/react-query"
import api, { regionAnalyticsApi } from "../api/api"


export const useFetchMyShortUrls = (token, onError) => {
    return useQuery({
        queryKey: ["my-shortenurls"],
        queryFn: async () => {
            return await api.get(
                "/api/urls/myurls",
                {
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        Authorization: "Bearer " + token,
                    },
                }
            );
        },
        select: (data) => {
            const sortedData = data.data.sort(
                (a, b) => new Date(b.createdDate) - new Date(a.createdDate)
            );
            return sortedData;
        },
        onError,
        staleTime: 5000,
    });
};

export const useFetchTotalClicks = (token, onError) => {
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(endDate.getDate() - 29);

    const start = startDate.toISOString().slice(0, 10);
    const end = endDate.toISOString().slice(0, 10);

    return useQuery({
        queryKey: ["url-totalclick", start, end],
        queryFn: async () => {
            return await api.get(
                `/api/urls/totalClicks?startDate=${start}&endDate=${end}`,
                {
                    headers: {
                        "Content-Type": "application/json",
                        Accept: "application/json",
                        Authorization: "Bearer " + token,
                    },
                }
            );
        },
        select: (data) => {
            // Backward compatibility: if backend still returns a date-keyed map, normalize it.
            if (data?.data && !Array.isArray(data.data) && !data.data.clicksByDate) {
                return Object.keys(data.data).map((key) => ({
                    clickDate: key,
                    count: data.data[key],
                }));
            }

            return data?.data?.clicksByDate ?? [];
        },
        onError,
        staleTime: 5000,
    });
};

export const useFetchRegionStats = (token, onError) => {
    return useQuery({
        queryKey: ["region-stats"],
        queryFn: async () => {
            const response = await regionAnalyticsApi.getRegionStats(token);
            return response?.data ?? [];
        },
        select: (data) => {
            if (!Array.isArray(data)) {
                return [];
            }

            return data
                .map((item) => ({
                    region: item?.region || "UNKNOWN",
                    count: Number(item?.count || 0),
                }))
                .sort((a, b) => b.count - a.count);
        },
        enabled: Boolean(token),
        onError,
        staleTime: 5000,
    });
};
